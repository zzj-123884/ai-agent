package org.example.aiagent.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体（P2：登录功能）。
 * <p>
 * 注意 password 存的是 PBKDF2 哈希而非明文 —— 用户密码是低熵的人类输入，
 * 必须用慢哈希 + 随机盐，这一点与 API Key 用 SHA-256 的策略不同（见 ApiKeyDigest 的说明）。
 */
@Data
public class User implements Serializable {

    private Long id;

    /**
     * 登录账号，唯一。
     */
    private String userAccount;

    /**
     * 昵称。
     */
    private String userName;

    /**
     * PBKDF2 密码哈希，格式为 base64(salt):base64(hash)。绝不可明文落库，也不可返回给前端。
     */
    private String userPassword;

    /**
     * 头像地址。
     */
    private String userAvatar;

    /**
     * 角色：user / admin。当前仅用于预留管理端能力。
     */
    private String userRole;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 是否被封禁：0 正常，1 封禁。
     */
    private Integer status;
}
