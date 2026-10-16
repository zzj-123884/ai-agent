-- AI 智能体任务表（P3 异步任务 + 可观测性）
-- 由 Spring Boot 的 spring.sql.init 在启动时执行，语句使用 IF NOT EXISTS 保证幂等。

CREATE TABLE IF NOT EXISTS ai_task
(
    id            BIGSERIAL PRIMARY KEY,
    -- 对外暴露的任务标识，前端用它查询进度，加唯一索引避免重复插入
    task_id       VARCHAR(64)  NOT NULL UNIQUE,
    user_id       BIGINT       NULL,
    api_key_id    BIGINT       NULL,
    -- 任务类型：MANUS / MEDICAL 等，便于分维度统计
    task_type     VARCHAR(32)  NOT NULL,
    chat_id       VARCHAR(64)  NULL,
    user_prompt   TEXT         NULL,
    result        TEXT         NULL,
    -- PENDING / RUNNING / SUCCEEDED / FAILED / STOPPED
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    current_step  INTEGER      NOT NULL DEFAULT 0,
    max_steps     INTEGER      NULL,
    error_message TEXT         NULL,
    start_time    TIMESTAMP    NULL,
    end_time      TIMESTAMP    NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 查询「某用户的最近任务」和「清理过期任务」都依赖这两列，建索引避免全表扫描
CREATE INDEX IF NOT EXISTS idx_ai_task_user_id ON ai_task (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_task_status_update ON ai_task (status, update_time);
CREATE INDEX IF NOT EXISTS idx_ai_task_create_time ON ai_task (create_time);

-- ==================== 用户表（P2 登录功能）====================

CREATE TABLE IF NOT EXISTS user_account
(
    id            BIGSERIAL PRIMARY KEY,
    -- 唯一约束是服务端唯一性校验的兵底：并发注册时 SELECT 后 INSERT 仍可能重复
    user_account  VARCHAR(64)  NOT NULL UNIQUE,
    user_name     VARCHAR(64)  NULL,
    -- 存储格式 base64(salt):base64(pbkdf2hash)，绝不存明文
    user_password VARCHAR(256) NOT NULL,
    user_avatar   VARCHAR(512) NULL,
    user_role     VARCHAR(32)  NOT NULL DEFAULT 'user',
    -- 0 正常，1 封禁
    status        INTEGER      NOT NULL DEFAULT 0,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== API Key 表（P2 开放平台计费）====================

CREATE TABLE IF NOT EXISTS user_api_key
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    -- 只存 SHA-256 摘要，不存明文。明文仅在创建时返回一次
    access_key_hash VARCHAR(64)  NOT NULL,
    secret_key_hash VARCHAR(64)  NOT NULL,
    name            VARCHAR(64)  NULL,
    -- 0 正常，1 禁用
    status          INTEGER      NOT NULL DEFAULT 0,
    -- NULL 表示不限配额
    total_quota     BIGINT       NULL,
    used_quota      BIGINT       NOT NULL DEFAULT 0,
    expire_time     TIMESTAMP    NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 鉴权时按 access_key_hash 精确匹配，必须建唯一索引，否则每次调用都是全表扫描
CREATE UNIQUE INDEX IF NOT EXISTS uk_api_key_access_hash ON user_api_key (access_key_hash);
CREATE INDEX IF NOT EXISTS idx_api_key_user_id ON user_api_key (user_id);
