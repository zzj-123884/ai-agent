package org.example.aiagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.auth.AuthInterceptor;
import org.example.aiagent.common.BaseResponse;
import org.example.aiagent.common.ErrorCode;
import org.example.aiagent.exception.BusinessException;
import org.example.aiagent.model.dto.user.UserLoginRequest;
import org.example.aiagent.model.dto.user.UserRegisterRequest;
import org.example.aiagent.model.entity.User;
import org.example.aiagent.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户接口（P2：登录功能）。
 * <p>
 * 登录态用 HttpSession 保存 userId。
 * <p>
 * 为什么用 session 而不是 JWT：
 * <ul>
 *   <li>本项目是单体部署 + 前后端同域（nginx 反代），session 天然可用，且登出即时生效；</li>
 *   <li>JWT 需要额外引入签名密钥管理、刷新令牌、黑名单，复杂度明显更高；</li>
 *   <li>若将来要做多实例横向扩展，把 session 换成 Redis 存储（Spring Session）即可，
 *       接口契约不变。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户接口模块")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest request) {
        long userId = userService.register(request);
        return BaseResponse.success(userId);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<User> login(@RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
        User user = userService.login(request);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }

        // 登录成功后才创建 session，避免为未登录请求分配 sessionId
        httpRequest.getSession(true).setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
        log.info("用户登录成功: {}", user.getUserAccount());
        return BaseResponse.success(user);
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public BaseResponse<Boolean> logout(HttpServletRequest httpRequest) {
        var session = httpRequest.getSession(false);
        if (session != null) {
            // invalidate 而不是只删属性：同时丢弃 session 内其它数据，防止残留
            session.invalidate();
        }
        return BaseResponse.success(true);
    }

    /**
     * 获取当前登录用户。
     * <p>
     * 该接口在拦截器的 excludePathPatterns 里，匿名访问返回未登录错误码而非 403，
     * 方便前端启动时用它做「是否已登录」的探测，不必捕获异常。
     */
    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录用户")
    public BaseResponse<User> getLoginUser(HttpServletRequest httpRequest) {
        var session = httpRequest.getSession(false);
        if (session == null) {
            return BaseResponse.error(ErrorCode.NOT_LOGIN_ERROR);
        }
        Object userId = session.getAttribute(AuthInterceptor.SESSION_USER_ID);
        if (!(userId instanceof Long)) {
            return BaseResponse.error(ErrorCode.NOT_LOGIN_ERROR);
        }
        return BaseResponse.success(userService.getSafeUser((Long) userId));
    }
}
