package org.example.aiagent.service.impl;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.auth.ApiKeyDigest;
import org.example.aiagent.model.entity.UserApiKey;
import org.example.aiagent.service.ApiKeyService;
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
 * API Key 服务实现。
 * <p>
 * Key 格式（参考业界通行做法，便于用户肉眼区分与轮换）：
 * <pre>
 * accessKey = ak_&lt;16位随机&gt;      公开标识，明文存库可行但这里也存哈希
 * secretKey = sk_&lt;32位随机&gt;      真正凭证，只存 SHA-256
 * </pre>
 * 校验时先按 accessKeyHash 命中唯一一行，再常量时间比对 secretKeyHash。
 */
@Slf4j
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 状态：正常。
     */
    private static final int STATUS_NORMAL = 0;

    /**
     * 状态：禁用。
     */
    private static final int STATUS_DISABLED = 1;

    private static final RowMapper<UserApiKey> ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserApiKey mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserApiKey key = new UserApiKey();
            key.setId(rs.getLong("id"));
            key.setUserId(rs.getLong("user_id"));
            key.setAccessKeyHash(rs.getString("access_key_hash"));
            key.setSecretKeyHash(rs.getString("secret_key_hash"));
            key.setName(rs.getString("name"));
            key.setStatus(rs.getInt("status"));
            key.setTotalQuota(getLongOrNull(rs, "total_quota"));
            key.setUsedQuota(getLongOrNull(rs, "used_quota"));
            key.setExpireTime(toLocalDateTime(rs.getTimestamp("expire_time")));
            key.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            key.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            return key;
        }
    };

    private static Long getLongOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    @Override
    public UserApiKey createApiKey(Long userId, String name, Long totalQuota) {
        String token = "sk-" + RandomUtil.randomString(48);
        String tokenHash = ApiKeyDigest.sha256(token);

        UserApiKey key = new UserApiKey();
        key.setUserId(userId);
        key.setName(name);
        key.setStatus(STATUS_NORMAL);
        key.setTotalQuota(totalQuota);
        key.setUsedQuota(0L);
        key.setCreateTime(LocalDateTime.now());
        key.setUpdateTime(LocalDateTime.now());
        // 只存令牌哈希。两列写入同一个哈希，以兼容原来的唯一索引
        key.setAccessKeyHash(tokenHash);
        key.setSecretKeyHash(tokenHash);

        jdbcTemplate.update("""
                INSERT INTO user_api_key
                    (user_id, access_key_hash, secret_key_hash, name, status,
                     total_quota, used_quota, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                key.getUserId(), key.getAccessKeyHash(), key.getSecretKeyHash(), key.getName(),
                key.getStatus(), key.getTotalQuota(), key.getUsedQuota(),
                Timestamp.valueOf(key.getCreateTime()), Timestamp.valueOf(key.getUpdateTime()));

        // 明文仅在本次响应中返回一次，不写库
        key.setPlainSecretKey(token);
        log.info("为用户 {} 签发了 API Key: {}", userId, name);
        return key;
    }

    @Override
    public UserApiKey validate(String accessKey, String secretKey) {
        if (accessKey == null || secretKey == null) {
            return null;
        }
        try {
            List<UserApiKey> keys = jdbcTemplate.query(
                    "SELECT * FROM user_api_key WHERE access_key_hash = ?",
                    ROW_MAPPER, ApiKeyDigest.sha256(accessKey));

            if (keys.isEmpty()) {
                log.warn("API Key 校验失败：accessKey 不存在");
                return null;
            }

            UserApiKey key = keys.get(0);

            // 常量时间比对，防止时序攻击推测 secretKey
            if (!ApiKeyDigest.matches(secretKey, key.getSecretKeyHash())) {
                log.warn("API Key 校验失败：secretKey 不匹配, keyId={}", key.getId());
                return null;
            }
            if (key.getStatus() == null || key.getStatus() != STATUS_NORMAL) {
                log.warn("API Key 校验失败：已禁用, keyId={}", key.getId());
                return null;
            }
            if (key.isExpired()) {
                log.warn("API Key 校验失败：已过期, keyId={}", key.getId());
                return null;
            }
            if (key.isQuotaExhausted()) {
                log.warn("API Key 校验失败：配额耗尽, keyId={}", key.getId());
                return null;
            }
            return key;
        } catch (Exception e) {
            log.error("API Key 校验异常", e);
            return null;
        }
    }

    @Override
    public UserApiKey validateBearer(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            List<UserApiKey> keys = jdbcTemplate.query(
                    "SELECT * FROM user_api_key WHERE secret_key_hash = ?",
                    ROW_MAPPER, ApiKeyDigest.sha256(token.trim()));
            if (keys.isEmpty()) {
                log.warn("Bearer 令牌校验失败：不存在");
                return null;
            }
            UserApiKey key = keys.get(0);
            if (key.getStatus() == null || key.getStatus() != STATUS_NORMAL) {
                log.warn("Bearer 令牌校验失败：已禁用, keyId={}", key.getId());
                return null;
            }
            if (key.isExpired()) {
                log.warn("Bearer 令牌校验失败：已过期, keyId={}", key.getId());
                return null;
            }
            if (key.isQuotaExhausted()) {
                log.warn("Bearer 令牌校验失败：配额耗尽, keyId={}", key.getId());
                return null;
            }
            return key;
        } catch (Exception e) {
            log.error("Bearer 令牌校验异常", e);
            return null;
        }
    }

    @Override
    public void incrementUsage(Long apiKeyId) {
        if (apiKeyId == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE user_api_key
                    SET used_quota = COALESCE(used_quota, 0) + 1, update_time = ?
                    WHERE id = ?
                    """,
                    Timestamp.valueOf(LocalDateTime.now()), apiKeyId);
        } catch (Exception e) {
            // 计费失败不应影响用户拿到结果
            log.error("累加 API Key 用量失败, keyId={}", apiKeyId, e);
        }
    }

    @Override
    public List<UserApiKey> listByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM user_api_key WHERE user_id = ? ORDER BY create_time DESC",
                ROW_MAPPER, userId);
    }

    @Override
    public boolean updateStatus(Long apiKeyId, Long userId, int status) {
        // WHERE 里带上 user_id：防止越权操作别人的 Key
        int rows = jdbcTemplate.update(
                "UPDATE user_api_key SET status = ?, update_time = ? WHERE id = ? AND user_id = ?",
                status, Timestamp.valueOf(LocalDateTime.now()), apiKeyId, userId);
        return rows > 0;
    }

    @Override
    public boolean deleteApiKey(Long apiKeyId, Long userId) {
        int rows = jdbcTemplate.update(
                "DELETE FROM user_api_key WHERE id = ? AND user_id = ?", apiKeyId, userId);
        return rows > 0;
    }
}
