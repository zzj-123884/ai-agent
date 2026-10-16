package org.example.aiagent.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 智能体任务记录。
 * <p>
 * 为什么要落库：推理任务耗时几十秒到几分钟，原来全靠同步等待 + SSE 长连接，
 * 一旦页面刷新、网络抖动，用户就彻底丢失了任务进度，服务端也无从知道哪个任务在跑、跑到哪一步。
 * <p>
 * 落库后带来三个能力：
 * <ol>
 *   <li>可观测：能查到「谁在什么时候问了什么、执行了几步、成功还是失败、耗时多久」；</li>
 *   <li>可恢复：前端断开后可用 taskId 重新查询结果；</li>
 *   <li>可计费：任务数与 token 消耗可作为 API Key 计费依据（配合 P2）。</li>
 * </ol>
 */
@Data
public class AiTask implements Serializable {

    /**
     * 主键。
     */
    private Long id;

    /**
     * 任务唯一标识，对外暴露，前端用它查询进度。
     */
    private String taskId;

    /**
     * 提交任务的用户 id。未登录/开放平台调用时为 null。
     */
    private Long userId;

    /**
     * 调用来源的 API Key id。用于计费归属。
     */
    private Long apiKeyId;

    /**
     * 任务类型，如 MANUS / MEDICAL，便于分维度统计。
     */
    private String taskType;

    /**
     * 会话 id，用于关联多轮对话。
     */
    private String chatId;

    /**
     * 用户输入。
     */
    private String userPrompt;

    /**
     * 最终结果文本。执行中为 null。
     */
    private String result;

    /**
     * 任务状态：PENDING / RUNNING / SUCCEEDED / FAILED / STOPPED。
     * 用字符串而非枚举，是为了避免数据库值域与代码枚举耦合，加新状态不用改表。
     */
    private String status;

    /**
     * 已执行步骤数，前端可展示「第 N 步 / 共 10 步」。
     */
    private Integer currentStep;

    /**
     * 最大步骤数。
     */
    private Integer maxSteps;

    /**
     * 失败原因。成功时为 null。
     */
    private String errorMessage;

    /**
     * 开始执行时间。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。用于计算耗时。
     */
    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
