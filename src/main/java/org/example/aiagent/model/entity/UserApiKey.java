package org.example.aiagent.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户 API Key。
 * <p>
 * 设计要点（参考开放平台做法）：
 * <ol>
 *   <li><b>只存哈希</b>：数据库里保存 accessKey / secretKey 的 SHA-256 摘要，
 *       不保存明文。即使库被拖走，攻击者也无法直接用这些值调用 API。
 *       明文只在创建时返回给用户一次。</li>
 *   <li><b>调用配额与计费</b>：totalQuota / usedQuota 支撑按次计费；
 *       每次成功调用后累加 usedQuota。</li>
 *   <li><b>状态与过期</b>：支持禁用（status）和时间过期（expireTime），
 *       两者任一不满足即拒绝调用。</li>
 * </ol>
 */
@Data
public class UserApiKey implements Serializable {

    private Long id;

    /**
     * 归属用户。开放平台场景下用于统计该用户下所有 Key 的总消耗。
     */
    private Long userId;

    /**
     * 访问密钥（公开部分），形如 ak_xxxx。可明文存储，因为它是标识而非凭证。
     * 但这里同样存哈希，避免被枚举出有效 Key 前缀做钓鱼。
     */
    private String accessKeyHash;

    /**
     * 签名密钥（私密部分）的哈希。这是真正的凭证，绝不可明文落库。
     */
    private String secretKeyHash;

    /**
     * 创建时给用户看一次的明文（不持久化，仅用于 create 接口的返回对象）。
     * 标记 {@code transient}，防止被序列化进响应或缓存。
     */
    private transient String plainSecretKey;

    /**
     * Key 名称，便于用户在控制台区分用途。
     */
    private String name;

    /**
     * 状态：0 正常，1 禁用。
     */
    private Integer status;

    /**
     * 总配额（可调用次数）。null 表示不限。
     */
    private Long totalQuota;

    /**
     * 已用配额。
     */
    private Long usedQuota;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 是否已过期。
     */
    public boolean isExpired() {
        return expireTime != null && expireTime.isBefore(LocalDateTime.now());
    }

    /**
     * 配额是否已耗尽。
     */
    public boolean isQuotaExhausted() {
        if (totalQuota == null) {
            return false;
        }
        return usedQuota != null && usedQuota >= totalQuota;
    }
}
