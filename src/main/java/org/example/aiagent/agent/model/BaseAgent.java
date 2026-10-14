package org.example.aiagent.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.internal.StringUtil;
import org.example.aiagent.manager.AiTaskCancellationManager;
import org.example.aiagent.sse.SseEvent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

@Data
@Slf4j
public abstract class BaseAgent {

    /**
     * SSE 事件的 JSON 序列化器。
     * 用静态实例而非每次 new，因为 ObjectMapper 构造开销大且线程安全。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String name;


    private String systemPrompt;
    private String nextStepPrompt;


    private AgentState state = AgentState.IDLE;


    private int maxSteps = 10;
    private int currentStep = 0;


    private ChatClient chatClient;


    private List<Message> messageList = new ArrayList<>();

    /**
     * 当前任务的唯一标识，用于取消查询。
     */
    private String taskId;

    /**
     * 取消标志位仓储。由调用方（Controller）注入，因为 Agent 本身是 new 出来的而非 Spring Bean。
     */
    private transient AiTaskCancellationManager cancellationManager;

    /**
     * 流式执行时的 emitter，供 step 内部推送思考/工具事件使用。
     */
    private transient SseEmitter currentEmitter;

    private transient IntConsumer progressListener;

    private transient Consumer<String> successListener;

    private transient Consumer<String> failureListener;


    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StringUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        state = AgentState.RUNNING;

        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);

                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }

            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {

            this.cleanup();
        }
    }

    public SseEmitter runStream(String userPrompt) {
        return runStream(userPrompt, null, null);
    }

    /**
     * 流式执行智能体，并把过程事件化推送给前端。
     *
     * @param userPrompt         用户输入
     * @param taskId             任务唯一标识；不为空时可被「停止」接口取消
     * @param cancellationManager 取消仓储；为 null 时不支持取消（兼容旧调用）
     */
    public SseEmitter runStream(String userPrompt, String taskId,
                                AiTaskCancellationManager cancellationManager) {
        return runStream(userPrompt, taskId, cancellationManager, null);
    }

    public SseEmitter runStream(String userPrompt, String taskId,
                                AiTaskCancellationManager cancellationManager,
                                IntConsumer progressListener) {
        return runStream(userPrompt, taskId, cancellationManager, progressListener, null, null);
    }

    public SseEmitter runStream(String userPrompt, String taskId,
                                AiTaskCancellationManager cancellationManager,
                                IntConsumer progressListener,
                                Consumer<String> successListener,
                                Consumer<String> failureListener) {

        this.taskId = taskId;
        this.cancellationManager = cancellationManager;
        this.progressListener = progressListener;
        this.successListener = successListener;
        this.failureListener = failureListener;

        SseEmitter emitter = new SseEmitter(300000L);
        this.currentEmitter = emitter;

        if (taskId != null && cancellationManager != null) {
            // 必须先注册再启动任务：否则用户在前几毫秒内点停止会找不到标志位而静默失败
            cancellationManager.register(taskId);
        }

        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sendEvent(emitter, SseEvent.error("无法从状态运行代理: " + this.state));
                    emitter.complete();
                    return;
                }
                if (StringUtil.isBlank(userPrompt)) {
                    sendEvent(emitter, SseEvent.error("不能使用空提示词运行代理"));
                    emitter.complete();
                    return;
                }

                state = AgentState.RUNNING;

                messageList.add(new UserMessage(userPrompt));

                List<String> stepResults = new ArrayList<>();

                try {
                    for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                        // 每一轮开始先检查取消标志：这是「停止」能真正生效的关键点
                        if (isCancelled()) {
                            state = AgentState.FINISHED;
                            log.info("任务 {} 被用户取消，已执行 {} 步", taskId, i);
                            sendEvent(emitter, SseEvent.stopped("已停止生成（共执行 " + i + " 步）"));
                            safeComplete(emitter);
                            return;
                        }

                        int stepNumber = i + 1;
                        currentStep = stepNumber;
                        if (progressListener != null) {
                            progressListener.accept(stepNumber);
                        }
                        log.info("Executing step " + stepNumber + "/" + maxSteps);

                        sendEvent(emitter, SseEvent.stepStart(stepNumber));

                        String stepResult = step();
                        if (isCancelled()) {
                            state = AgentState.FINISHED;
                            sendEvent(emitter, SseEvent.stopped("已停止生成"));
                            safeComplete(emitter);
                            return;
                        }
                        if (stepResult != null) {
                            stepResults.add("Step " + stepNumber + ": " + stepResult);
                        }
                    }

                    if (currentStep >= maxSteps) {
                        state = AgentState.FINISHED;
                        String error = "执行结束: 达到最大步骤 (" + maxSteps + ")";
                        if (failureListener != null) {
                            failureListener.accept(error);
                        }
                        sendEvent(emitter, SseEvent.error(error));
                    } else {
                        String result = String.join("\n", stepResults);
                        if (successListener != null) {
                            successListener.accept(result);
                        }
                        sendEvent(emitter, SseEvent.done(result));
                    }
                    safeComplete(emitter);
                } catch (Exception e) {
                    state = AgentState.ERROR;
                    log.error("执行智能体失败", e);
                    if (failureListener != null) {
                        failureListener.accept(e.getMessage());
                    }
                    sendEvent(emitter, SseEvent.error(e.getMessage()));
                    safeComplete(emitter);
                } finally {
                    this.cleanup();
                    if (taskId != null && cancellationManager != null) {
                        // 统一在 finally 里注销，避免内存泄漏；重复注销是幂等的
                        cancellationManager.unregister(taskId);
                    }
                }
            } catch (Exception e) {
                log.error("执行智能体异常", e);
                if (failureListener != null) {
                    failureListener.accept(e.getMessage());
                }
                emitter.completeWithError(e);
            } finally {
                this.currentEmitter = null;
            }
        });

        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        emitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return emitter;
    }

    /**
     * 查询当前任务是否已被取消。
     */
    protected boolean isCancelled() {
        return taskId != null && cancellationManager != null
                && cancellationManager.isCancelled(taskId);
    }

    /**
     * 向 emitter 推送一个事件。
     * <p>
     * 序列化失败不能影响主流程：事件只是「展示用」，不应因为一个 JSON 错误让整个推理失败。
     */
    protected void emit(SseEvent event) {
        if (currentEmitter != null) {
            sendEvent(currentEmitter, event);
        }
    }

    /**
     * 推送思考内容。子类在模型返回后调用，让前端能展示「思考过程」。
     */
    protected void emitThink(String text) {
        if (StringUtil.isBlank(text)) {
            return;
        }
        emit(SseEvent.think(currentStep, text));
    }

    /**
     * 推送工具调用信息。
     */
    protected void emitToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            emit(SseEvent.toolCall(currentStep, toolCall.name(), toolCall.arguments()));
        }
    }

    /**
     * 推送工具执行结果。
     */
    protected void emitToolResult(String toolName, String result) {
        emit(SseEvent.toolResult(currentStep, toolName, result));
    }

    /**
     * 推送最终回答正文。
     */
    protected void emitAnswer(String text) {
        if (StringUtil.isBlank(text)) {
            return;
        }
        emit(SseEvent.answer(text));
    }

    private void sendEvent(SseEmitter emitter, SseEvent event) {
        if (isCancelled() && !"STOPPED".equals(event.getType())) {
            return;
        }
        try {
            // 统一给每个事件贴上 taskId，前端才能随时发起停止请求
            if (event.getTaskId() == null && this.taskId != null) {
                event.setTaskId(this.taskId);
            }
            // 用具名事件 "message" + 标准 data 字段，前端单个 onmessage 即可接收全部类型，
            // 无需为每个 type 注册 addEventListener
            String json = OBJECT_MAPPER.writeValueAsString(event);
            emitter.send(SseEmitter.event().data(json, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            // 客户端可能已断开，此时 send 会失败，属于正常流程，只记 debug
            log.debug("推送 SSE 事件失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 完成 emitter 前先判断是否已断开，避免抛 IllegalStateException。
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("完成 SSE 连接时异常（客户端可能已断开）: {}", e.getMessage());
        }
    }


    public abstract String step();


    protected void cleanup() {

    }
}
