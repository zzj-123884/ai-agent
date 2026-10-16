package org.example.aiagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.agent.model.Manus_deepseek;
import org.example.aiagent.app.MedicalAssistant;
import org.example.aiagent.auth.AuthContext;
import org.example.aiagent.common.BaseResponse;
import org.example.aiagent.common.ErrorCode;
import org.example.aiagent.exception.BusinessException;
import org.example.aiagent.manager.AiTaskCancellationManager;
import org.example.aiagent.model.entity.AiTask;
import org.example.aiagent.model.enums.AiTaskStatus;
import org.example.aiagent.service.AiTaskService;
import org.example.aiagent.service.ApiKeyService;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;

/**
 * AI 接口模块。
 * <p>
 * 本次改造覆盖 5 个方面：
 * <ol>
 *   <li><b>统一响应（P1）</b>：同步接口返回 BaseResponse，异常由 GlobalExceptionHandler 统一转换。</li>
 *   <li><b>鉴权（P2）</b>：整个 /ai/** 被 AuthInterceptor 保护，支持 session 与 API Key 双通道。</li>
 *   <li><b>异步任务（P3）</b>：提交前落库任务记录，可用 taskId 查进度。</li>
 *   <li><b>停止生成（P5）</b>：/ai/task/stop 置取消标志，Agent 循环下一步即收敛。</li>
 *   <li><b>结构化事件（P6）</b>：SSE 推送 JSON 事件，区分思考与回答。</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI接口模块")
public class AiController {

    @Resource
    private MedicalAssistant medicalAssistant;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private DeepSeekChatModel deepSeekChatModel;

    @Resource
    private AiTaskService aiTaskService;

    @Resource
    private AiTaskCancellationManager cancellationManager;

    @Resource
    private ApiKeyService apiKeyService;

    private static final String TASK_TYPE_MEDICAL = "MEDICAL";
    private static final String TASK_TYPE_MANUS = "MANUS";

    // ==================== 医学助手 ====================

    @GetMapping("/medical/chat/sync")
    @Operation(summary = "医学助手-同步对话")
    public BaseResponse<String> doChatWithMedicalSync(
            @RequestParam String message,
            @RequestParam(required = false) String chatId) {

        validateMessage(message);

        AiTask task = createTask(TASK_TYPE_MEDICAL, chatId, message, 1);
        String taskId = task != null ? task.getTaskId() : null;
        try {
            if (taskId != null) {
                aiTaskService.markRunning(taskId);
            }
            String content = medicalAssistant.doChat(message, chatId);
            if (taskId != null) {
                aiTaskService.markSucceeded(taskId, content);
            }
            chargeApiKey();
            return BaseResponse.success(content);
        } catch (Exception e) {
            log.error("医学助手同步对话失败, taskId={}", taskId, e);
            if (taskId != null) {
                aiTaskService.markFailed(taskId, e.getMessage());
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败，请稍后重试");
        }
    }

    @GetMapping(value = "/medical/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "医学助手-流式对话")
    public Flux<String> doChatWithMedicalSSE(
            @RequestParam String message,
            @RequestParam(required = false) String chatId) {

        validateMessage(message);
        AiTask task = createTask(TASK_TYPE_MEDICAL, chatId, message, 1);
        String taskId = task != null ? task.getTaskId() : null;
        Long apiKeyId = AuthContext.isApiKeyAuth() ? AuthContext.getApiKeyId() : null;

        if (taskId != null) {
            aiTaskService.markRunning(taskId);
        }

        StringBuilder buffer = new StringBuilder();

        return medicalAssistant.doChatByStream(message, chatId)
                .doOnNext(buffer::append)
                .doFinally(signal -> {
                    if (taskId != null) {
                        // SignalType.ON_COMPLETE 表示流正常跑完；
                        // CANCEL / ON_ERROR 都归为「未完整生成」，避免把半截结果标成成功
                        if (signal == reactor.core.publisher.SignalType.ON_COMPLETE) {
                            aiTaskService.markSucceeded(taskId, buffer.toString());
                        } else if (signal == reactor.core.publisher.SignalType.CANCEL) {
                            aiTaskService.markStopped(taskId, 1);
                        } else {
                            aiTaskService.markFailed(taskId, "医学助手流式响应失败");
                        }
                    }
                    if (signal == reactor.core.publisher.SignalType.ON_COMPLETE) {
                        chargeApiKey(apiKeyId);
                    }
                });
    }

    /**
     * Manus 智能体，结构化 SSE 事件流（P5 + P6 的核心接口）。
     */
    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "超级智能体-流式对话（结构化事件）")
    public SseEmitter doChatWithManus(
            @RequestParam String message,
            @RequestParam(required = false) String chatId) {

        validateMessage(message);

        // 先建任务拿到 taskId，再交给 Agent：这样停止请求在 Agent 刚启动时就能命中标志位
        AiTask task = createTask(TASK_TYPE_MANUS, chatId, message, 10);
        String taskId = task != null ? task.getTaskId() : null;
        Long apiKeyId = AuthContext.isApiKeyAuth() ? AuthContext.getApiKeyId() : null;

        if (taskId != null) {
            aiTaskService.markRunning(taskId);
        }

        // 每次请求 new 一个实例：Agent 持有 messageList 状态，复用 Bean 会让多用户对话互相污染
        Manus_deepseek manus = new Manus_deepseek(allTools, deepSeekChatModel);

        SseEmitter emitter = manus.runStream(message, taskId, cancellationManager,
                step -> aiTaskService.updateProgress(taskId, step),
                result -> {
                    aiTaskService.markSucceeded(taskId, result);
                    chargeApiKey(apiKeyId);
                },
                error -> aiTaskService.markFailed(taskId, error));

        emitter.onCompletion(() -> {
            if (taskId != null) {
                AiTask current = aiTaskService.getByTaskId(taskId);
                // 已写入终态的不要覆盖（Agent 出错时已 markFailed）
                if (current != null && !AiTaskStatus.isTerminal(current.getStatus())) {
                    if (cancellationManager.isCancelled(taskId)) {
                        int step = current.getCurrentStep() == null ? 0 : current.getCurrentStep();
                        aiTaskService.markStopped(taskId, step);
                    } else {
                        aiTaskService.markSucceeded(taskId, current.getResult());
                    }
                }
                cancellationManager.unregister(taskId);
            }
        });

        emitter.onTimeout(() -> {
            log.warn("Manus 任务超时, taskId={}", taskId);
            if (taskId != null) {
                aiTaskService.markFailed(taskId, "SSE 连接超时");
                cancellationManager.unregister(taskId);
            }
        });

        return emitter;
    }

    // ==================== 任务管理（P3 / P5）====================

    /**
     * 手动停止生成。
     * <p>
     * 用 POST 而非 GET：这是有副作用的写操作，GET 语义应为幂等只读，
     * 且可能被浏览器预取导致误停。
     */
    @PostMapping("/task/stop")
    @Operation(summary = "停止正在生成的 AI 任务")
    public BaseResponse<Boolean> stopTask(@RequestParam String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskId 不能为空");
        }
        AiTask task = requireOwnedTask(taskId);
        boolean cancelled = cancellationManager.cancel(taskId);
        if (cancelled) {
            int currentStep = task.getCurrentStep() == null ? 0 : task.getCurrentStep();
            aiTaskService.markStopped(taskId, currentStep);
        }
        return BaseResponse.success(cancelled);
    }

    /**
     * 查询任务状态，用于页面刷新后恢复进度。
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "查询任务状态")
    public BaseResponse<AiTask> getTask(@PathVariable String taskId) {
        AiTask task = requireOwnedTask(taskId);
        return BaseResponse.success(task);
    }

    @GetMapping("/task/running/count")
    @Operation(summary = "运行中任务数")
    public BaseResponse<Integer> runningTaskCount() {
        return BaseResponse.success(cancellationManager.runningTaskCount());
    }

    // ==================== 私有辅助 ====================

    /**
     * 校验用户输入。
     */
    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }
        if (message.length() > 4000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息过长，请控制在 4000 字以内");
        }
    }

    private AiTask requireOwnedTask(String taskId) {
        AiTask task = aiTaskService.getByTaskId(taskId);
        if (task == null || !java.util.Objects.equals(task.getUserId(), AuthContext.getUserId())) {
            // 对不存在与不属于当前用户的任务统一返回 404，避免泄露 taskId 是否有效
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }
        return task;
    }

    /**
     * 创建任务记录。落库失败时返回 null，不阻断主流程。
     */
    private AiTask createTask(String taskType, String chatId, String message, int maxSteps) {
        try {
            return aiTaskService.createTask(taskType, chatId, message,
                    AuthContext.getUserId(), AuthContext.getApiKeyId(), maxSteps);
        } catch (Exception e) {
            log.error("创建任务记录失败，任务仍将继续执行", e);
            return null;
        }
    }

    /**
     * API Key 调用计数。
     * <p>
     * 只有通过 API Key 认证的请求才计数；session 登录用户不计费，
     * 否则会把本站用户也算进开放平台配额。
     */
    private void chargeApiKey() {
        if (AuthContext.isApiKeyAuth()) {
            chargeApiKey(AuthContext.getApiKeyId());
        }
    }

    private void chargeApiKey(Long apiKeyId) {
        if (apiKeyId != null) {
            apiKeyService.incrementUsage(apiKeyId);
        }
    }
}
