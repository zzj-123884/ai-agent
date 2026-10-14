package org.example.aiagent.model.enums;

/**
 * 任务状态常量。
 * <p>
 * 用常量类而不是 enum：数据库存的是 String，避免每次读写都要做 enum <-> String 转换，
 * 同时新增状态时不会有「枚举里没有这个值」的反序列化异常。
 */
public final class AiTaskStatus {

    /**
     * 已创建，等待执行。
     */
    public static final String PENDING = "PENDING";

    /**
     * 执行中。
     */
    public static final String RUNNING = "RUNNING";

    /**
     * 执行成功。
     */
    public static final String SUCCEEDED = "SUCCEEDED";

    /**
     * 执行失败。
     */
    public static final String FAILED = "FAILED";

    /**
     * 用户主动停止。
     */
    public static final String STOPPED = "STOPPED";

    /**
     * 是否为终态。终态任务不应再被更新（除补充结果外）。
     */
    public static boolean isTerminal(String status) {
        return SUCCEEDED.equals(status) || FAILED.equals(status) || STOPPED.equals(status);
    }

    private AiTaskStatus() {
    }
}
