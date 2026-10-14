package org.example.aiagent.auth;

import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.common.BaseResponse;
import org.example.aiagent.common.ErrorCode;
import org.example.aiagent.model.entity.UserApiKey;
import org.example.aiagent.service.ApiKeyService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;

/**
 * AI 接口鉴权拦截器（P2：登录 + API Key 双通道）。
 * <p>
 * 支持两种身份，满足两类调用方：
 * <ol>
 *   <li><b>浏览器用户</b>：通过 session 登录（{@code /user/login} 写入 session），
 *       会话内直接调用，无需每次传 Key。适合本站前端。</li>
 *   <li><b>第三方应用</b>：通过请求头 {@code X-Access-Key} / {@code X-Secret-Key} 调用。
 *       适合开放平台接入，便于按 Key 计费与配额控制。</li>
 * </ol>
 * <p>
 * 设计取舍——为什么不用 Spring Security：
 * 本项目下一步要做的是「登录 + Key 双通道 + 配额」，Spring Security 能覆盖，
 * 但会引入整套 filter chain、CSRF、PasswordEncoder 配置，对当前复杂度是过度设计。
 * 一个显式拦截器的可读性和可控性更好，未来需要再迁移。
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_ACCESS_KEY = "X-Access-Key";
    public static final String HEADER_SECRET_KEY = "X-Secret-Key";
    public static final String SESSION_USER_ID = "LOGIN_USER_ID";

    @Resource
    private ApiKeyService apiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 浏览器预检请求直接放行，否则跨域调用会在 OPTIONS 阶段被拦下
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // -------- 通道 1：Bearer 令牌（第三方调用的标准方式）--------
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            UserApiKey apiKey = apiKeyService.validateBearer(token);
            if (apiKey == null) {
                reject(request, response, ErrorCode.API_KEY_INVALID);
                return false;
            }
            AuthContext.setUserId(apiKey.getUserId());
            AuthContext.setApiKeyId(apiKey.getId());
            AuthContext.setApiKeyAuth(true);
            return true;
        }

        // -------- 通道 2：session 登录 --------
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userId = session.getAttribute(SESSION_USER_ID);
            if (userId instanceof Long) {
                AuthContext.setUserId((Long) userId);
                return true;
            }
        }

        // -------- 通道 3：旧的双请求头，仍可用 --------
        String accessKey = request.getHeader(HEADER_ACCESS_KEY);
        String secretKey = request.getHeader(HEADER_SECRET_KEY);

        if (accessKey != null && secretKey != null) {
            UserApiKey apiKey = apiKeyService.validate(accessKey, secretKey);
            if (apiKey == null) {
                reject(request, response, ErrorCode.API_KEY_INVALID);
                return false;
            }
            AuthContext.setUserId(apiKey.getUserId());
            AuthContext.setApiKeyId(apiKey.getId());
            AuthContext.setApiKeyAuth(true);
            return true;
        }

        // 两条通道都不满足 -> 拒绝
        reject(request, response, ErrorCode.NOT_LOGIN_ERROR);
        return false;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws Exception {
        String path = request.getServletPath();
        if (path != null && path.startsWith("/v1/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String json = "{\"error\":{\"message\":\"" + errorCode.getMessage()
                    + "\",\"type\":\"authentication_error\"}}";
            response.getWriter().write(json);
            response.getWriter().flush();
            return;
        }
        writeError(response, errorCode);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        // 必须清理：Tomcat 复用线程，残留身份会导致下一个请求越权
        AuthContext.clear();
    }

    /**
     * 直接写 JSON 错误响应。
     * <p>
     * 拦截器在 Controller 之前执行，抛出的异常不会（容易）被 @RestControllerAdvice 处理，
     * 所以这里手工序列化，保证前端拿到的仍是统一的 BaseResponse 结构。
     */
    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        // 用 TEXT 而非 APPLICATION_JSON：SSE 请求的 Accept 可能是 text/event-stream，
        // 强行返回 json 会让浏览器 EventSource 抛错而非进入 onerror 分支处理
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        BaseResponse<?> body = BaseResponse.error(errorCode);
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                body.getCode(), body.getMessage());
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
