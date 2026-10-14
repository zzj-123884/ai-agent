package org.example.aiagent.service;

import org.example.aiagent.model.entity.UserApiKey;

/**
 * API Key 服务（P2：开放平台调用与计费）。
 */
public interface ApiKeyService {

    /**
     * 为用户签发一个新的 API Key。
     * <p>
     * 返回对象中的 {@code plainSecretKey} 是明文，**仅此一次可见**，务必立即展示给用户。
     *
     * @param userId     归属用户
     * @param name       Key 名称
     * @param totalQuota 总配额，null 表示不限
     * @return 含明文 secretKey 的实体（未持久化该明文）
     */
    UserApiKey createApiKey(Long userId, String name, Long totalQuota);

    /**
     * 校验 API Key 是否可用。
     * <p>
     * 校验维度：存在性、状态、过期时间、配额。
     *
     * @param accessKey 公开部分
     * @param secretKey 签名部分
     * @return 可用的 Key；不可用时返回 null
     */
    UserApiKey validate(String accessKey, String secretKey);

    /**
     * 校验 Authorization: Bearer 令牌。令牌明文只在签发时返回一次。
     */
    UserApiKey validateBearer(String token);

    /**
     * 累加调用次数。在调用成功后执行，失败不计数（避免用户为报错付钱）。
     */
    void incrementUsage(Long apiKeyId);

    /**
     * 查询用户的 Key 列表（不含明文与哈希）。
     */
    java.util.List<UserApiKey> listByUserId(Long userId);

    /**
     * 禁用 / 启用。
     */
    boolean updateStatus(Long apiKeyId, Long userId, int status);

    /**
     * 删除自己的 Key。
     */
    boolean deleteApiKey(Long apiKeyId, Long userId);
}
