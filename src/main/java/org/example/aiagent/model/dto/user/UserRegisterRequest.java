package org.example.aiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求。
 */
@Data
public class UserRegisterRequest implements Serializable {

    private String userAccount;

    private String userPassword;

    /**
     * 确认密码。服务端必须校验它等于 userPassword，
     * 否则用户输错一次就永久登录不上，是常见体验坑。
     */
    private String checkPassword;

    private String userName;
}
