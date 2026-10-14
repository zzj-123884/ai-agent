package org.example.aiagent.service;

import org.example.aiagent.model.dto.user.UserLoginRequest;
import org.example.aiagent.model.dto.user.UserRegisterRequest;
import org.example.aiagent.model.entity.User;

/**
 * 用户服务（P2：登录功能）。
 */
public interface UserService {

    /**
     * 注册。
     * <p>
     * 要求：账号唯一、长度合规、两次密码一致。密码入库前用 BCrypt 哈希。
     *
     * @return 新用户 id
     */
    long register(UserRegisterRequest request);

    /**
     * 登录。
     *
     * @return 登录成功的用户（已脱敏，不含密码哈希）；失败返回 null
     */
    User login(UserLoginRequest request);

    /**
     * 查询用户，返回脱敏后的对象。
     */
    User getSafeUser(Long userId);

    /**
     * 判断是否为管理员。
     */
    boolean isAdmin(User user);
}
