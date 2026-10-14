package org.example.aiagent.auth;

/**
 * 当前请求的身份上下文。
 * <p>
 * 用 ThreadLocal 保存的原因：鉴权发生在拦截器里，而业务代码（任务落库需要记 userId / apiKeyId）
 * 在 Controller/Service 中，两者之间隔着 Spring MVC 的调用栈。
 * 与其给每个 Controller 方法加参数，不如用上下文传递。
 * <p>
 * <b>必须在请求结束时调用 {@link #clear()}</b>：Tomcat 线程是复用的，
 * 不清理会让下一个请求读到上一个请求的身份，造成越权。
 */
public final class AuthContext {

    /**
     * 登录用户 id。未登录为 null。
     */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 本次调用使用的 API Key id。走 session 登录时为 null。
     */
    private static final ThreadLocal<Long> API_KEY_ID = new ThreadLocal<>();

    /**
     * 是否通过 API Key 方式认证。
     */
    private static final ThreadLocal<Boolean> API_KEY_AUTH = ThreadLocal.withInitial(() -> false);

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setApiKeyId(Long apiKeyId) {
        API_KEY_ID.set(apiKeyId);
    }

    public static Long getApiKeyId() {
        return API_KEY_ID.get();
    }

    public static void setApiKeyAuth(boolean value) {
        API_KEY_AUTH.set(value);
    }

    public static boolean isApiKeyAuth() {
        return Boolean.TRUE.equals(API_KEY_AUTH.get());
    }

    /**
     * 请求结束时清理，防止线程复用导致身份串号。
     */
    public static void clear() {
        USER_ID.remove();
        API_KEY_ID.remove();
        API_KEY_AUTH.remove();
    }
}
