package org.example.aiagent.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.model.entity.AiTask;
import org.example.aiagent.model.enums.AiTaskStatus;
import org.example.aiagent.service.AiTaskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于 JdbcTemplate 的任务状态持久化实现。
 * <p>
 * 为什么不用 JPA：项目当前已有 {@code spring-boot-starter-jdbc}（PgVector 依赖它），
 * 引入 JPA 意味着额外一套 Session/事务抽象和实体扫描，为一个单表 CRUD 不划算。
 * JdbcTemplate 直接、可预期，且与现有依赖一致。
 * <p>
 * 容错原则：任务记录是「观测设施」，不是核心业务链路。
 * 因此所有写库操作都吞掉异常只记日志 —— 不能因为记录状态失败而让用户的 AI 请求也失败。
 */
@Slf4j
@Service
public class AiTaskServiceImpl implements AiTaskService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<AiTask> ROW_MAPPER = new RowMapper<>() {
        @Override
        public AiTask mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiTask task = new AiTask();
            task.setId(rs.getLong("id"));
            task.setTaskId(rs.getString("task_id"));
            task.setUserId(getLongOrNull(rs, "user_id"));
            task.setApiKeyId(getLongOrNull(rs, "api_key_id"));
            task.setTaskType(rs.getString("task_type"));
            task.setChatId(rs.getString("chat_id"));
            task.setUserPrompt(rs.getString("user_prompt"));
            task.setResult(rs.getString("result"));
            task.setStatus(rs.getString("status"));
            task.setCurrentStep(getIntOrNull(rs, "current_step"));
            task.setMaxSteps(getIntOrNull(rs, "max_steps"));
            task.setErrorMessage(rs.getString("error_message"));
            task.setStartTime(toLocalDateTime(rs.getTimestamp("start_time")));
            task.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
            task.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            task.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return task;
        }
    };

    /**
     * 读取 Long 列，允许 NULL。
     * getLong 遇到 NULL 会返回 0，会让「无 apiKeyId」变成 0 从而被误认为有效外键。
     */
    private static Long getLongOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public AiTask createTask(String taskType, String chatId, String userPrompt,
                             Long userId, Long apiKeyId, int maxSteps) {
        AiTask task = new AiTask();
        task.setTaskId("task_" + System.currentTimeMillis() + "_"
                + IdUtil.fastSimpleUUID().substring(0, 8));
        task.setTaskType(taskType);
        task.setChatId(chatId);
        task.setUserPrompt(userPrompt);
        task.setUserId(userId);
        task.setApiKeyId(apiKeyId);
        task.setMaxSteps(maxSteps);
        task.setCurrentStep(0);
        task.setStatus(AiTaskStatus.PENDING);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_task
                        (task_id, user_id, api_key_id, task_type, chat_id, user_prompt,
                         status, current_step, max_steps, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    task.getTaskId(), task.getUserId(), task.getApiKeyId(), task.getTaskType(),
                    task.getChatId(), task.getUserPrompt(), task.getStatus(),
                    task.getCurrentStep(), task.getMaxSteps(),
                    Timestamp.valueOf(task.getCreateTime()), Timestamp.valueOf(task.getUpdateTime()));
        } catch (Exception e) {
            // 建表失败或数据库不可用时，任务仍应继续执行，只是没有观测记录
            log.error("创建任务记录失败, taskId={}", task.getTaskId(), e);
        }
        return task;
    }

    @Override
    public void markRunning(String taskId) {
        update("UPDATE ai_task SET status = ?, start_time = ?, update_time = ? WHERE task_id = ?",
                AiTaskStatus.RUNNING, Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()), taskId);
    }

    @Override
    public void updateProgress(String taskId, int currentStep) {
        update("UPDATE ai_task SET current_step = ?, update_time = ? WHERE task_id = ? AND status = ?",
                currentStep, Timestamp.valueOf(LocalDateTime.now()), taskId, AiTaskStatus.RUNNING);
    }

    @Override
    public void markSucceeded(String taskId, String result) {
        LocalDateTime now = LocalDateTime.now();
        update("""
                UPDATE ai_task SET status = ?, result = ?, end_time = ?, update_time = ?
                WHERE task_id = ? AND status IN (?, ?)
                """,
                AiTaskStatus.SUCCEEDED, truncate(result), Timestamp.valueOf(now),
                Timestamp.valueOf(now), taskId, AiTaskStatus.PENDING, AiTaskStatus.RUNNING);
    }

    @Override
    public void markFailed(String taskId, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        update("""
                UPDATE ai_task SET status = ?, error_message = ?, end_time = ?, update_time = ?
                WHERE task_id = ? AND status IN (?, ?)
                """,
                AiTaskStatus.FAILED, truncate(errorMessage), Timestamp.valueOf(now),
                Timestamp.valueOf(now), taskId, AiTaskStatus.PENDING, AiTaskStatus.RUNNING);
    }

    @Override
    public void markStopped(String taskId, int executedSteps) {
        LocalDateTime now = LocalDateTime.now();
        update("""
                UPDATE ai_task SET status = ?, current_step = ?, end_time = ?, update_time = ?
                WHERE task_id = ? AND status IN (?, ?)
                """,
                AiTaskStatus.STOPPED, executedSteps, Timestamp.valueOf(now),
                Timestamp.valueOf(now), taskId, AiTaskStatus.PENDING, AiTaskStatus.RUNNING);
    }

    @Override
    public AiTask getByTaskId(String taskId) {
        try {
            List<AiTask> tasks = jdbcTemplate.query(
                    "SELECT * FROM ai_task WHERE task_id = ?", ROW_MAPPER, taskId);
            return tasks.isEmpty() ? null : tasks.get(0);
        } catch (Exception e) {
            log.error("查询任务失败, taskId={}", taskId, e);
            return null;
        }
    }

    @Override
    public int cleanExpiredTasks(int retentionHours) {
        try {
            return jdbcTemplate.update("""
                    DELETE FROM ai_task
                    WHERE status IN (?, ?, ?)
                      AND update_time < ?
                    """,
                    AiTaskStatus.SUCCEEDED, AiTaskStatus.FAILED, AiTaskStatus.STOPPED,
                    Timestamp.valueOf(LocalDateTime.now().minusHours(retentionHours)));
        } catch (Exception e) {
            log.error("清理过期任务失败", e);
            return 0;
        }
    }

    private void update(String sql, Object... args) {
        try {
            jdbcTemplate.update(sql, args);
        } catch (Exception e) {
            log.error("更新任务状态失败, sql={}", sql, e);
        }
    }

    /**
     * 截断超长文本。
     * <p>
     * user_prompt / result 列在 schema 里是 TEXT，但 AI 的输出可能达到几百 KB，
     * 全部落库既拖慢写入也没必要——观测关注的是「有没有成功」，不是完整存档。
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        int maxLength = 10_000;
        return text.length() > maxLength ? text.substring(0, maxLength) + "...[已截断]" : text;
    }
}
