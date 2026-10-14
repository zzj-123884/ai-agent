package org.example.aiagent.service;

import org.example.aiagent.model.entity.AiTask;

/**
 * AI 任务记录服务。
 * <p>
 * 抽成接口的原因：任务状态存储的落地方式可能变（先用内存/文件跑通，后续换 MySQL），
 * Controller 只依赖接口，换实现不用改调用方。
 */
public interface AiTaskService {

    /**
     * 创建任务记录，状态为 PENDING。
     *
     * @return 生成的任务（含 taskId）
     */
    AiTask createTask(String taskType, String chatId, String userPrompt,
                      Long userId, Long apiKeyId, int maxSteps);

    /**
     * 标记任务开始执行。
     */
    void markRunning(String taskId);

    /**
     * 更新进度。
     *
     * @param currentStep 已执行到的步骤序号
     */
    void updateProgress(String taskId, int currentStep);

    /**
     * 标记成功并写入结果。
     */
    void markSucceeded(String taskId, String result);

    /**
     * 标记失败并记录原因。
     */
    void markFailed(String taskId, String errorMessage);

    /**
     * 标记为用户主动停止。
     */
    void markStopped(String taskId, int executedSteps);

    /**
     * 查询任务。不存在时返回 null。
     */
    AiTask getByTaskId(String taskId);

    /**
     * 清理过期的终态任务，避免表无限增长。
     *
     * @param retentionHours 保留小时数
     * @return 清理条数
     */
    int cleanExpiredTasks(int retentionHours);
}
