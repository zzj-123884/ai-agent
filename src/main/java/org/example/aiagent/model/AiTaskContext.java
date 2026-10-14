package org.example.aiagent.model.entity;

import cn.hutool.core.util.IdUtil;
import lombok.Data;
import org.example.aiagent.model.enums.AiTaskStatus;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存态任务运行上下文。
 * <p>
 * 与 {@link AiTask}（持久化实体）分开的原因：
 * <ul>
 *   <li>持久化实体是数据库的镜像，字段变更受表结构约束；</li>
 *   <li>运行上下文持有非持久化状态（累计步骤、token 计数），且需要高频更新，</li>
 *   <li>把两者混在一起会导致「为了改一个内存计数而写库」，性能很差。</li>
 * </ul>
 * <p>
 * 这里的实现只用 {@link AtomicInteger} 保证计数线程安全；真正的持久化由
 * {@link org.example.aiagent.service.AiTaskService} 负责。
 */
@Data
public class AiTaskContext {

    private final String taskId;

    private final String taskType;

    private final String chatId;

    private final String userPrompt;

    private final Long userId;

    private final Long apiKeyId;

    private final int maxSteps;

    /**
     * 已执行步骤数。用 AtomicInteger 因为进度更新可能来自回调线程。
     */
    private final AtomicInteger executedSteps = new AtomicInteger(0);

    private volatile String status = AiTaskStatus.PENDING;

    private final LocalDateTime createTime = LocalDateTime.now();

    private volatile LocalDateTime endTime;

    private volatile String result;

    private volatile String errorMessage;

    public AiTaskContext(String taskType, String chatId, String userPrompt,
                         Long userId, Long apiKeyId, int maxSteps) {
        this.taskId = generateTaskId();
        this.taskType = taskType;
        this.chatId = chatId;
        this.userPrompt = userPrompt;
        this.userId = userId;
        this.apiKeyId = apiKeyId;
        this.maxSteps = maxSteps;
    }

    /**
     * 生成任务 id。
     * <p>
     * 用时间戳 + 随机短码：时间戳保证大致有序（便于按时间排序排查），
     * 随机码避免同一毫秒内并发提交产生碰撞。
     */
    private static String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + IdUtil.fastSimpleUUID().substring(0, 8);
    }

    public void incrementStep() {
        executedSteps.incrementAndGet();
    }

    public int getExecutedSteps() {
        return executedSteps.get();
    }
}
