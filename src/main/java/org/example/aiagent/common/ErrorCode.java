package org.example.aiagent.common;

/**
 * 统一错误码定义。
 * <p>
 * 约定：0 表示成功；1xxxx 客户端错误；2xxxx 服务端错误；3xxxx 第三方依赖错误。
 * 前端只需判断 code 是否为 0，非 0 时直接展示 message。
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),

    // ===== 客户端错误 =====
    PARAMS_ERROR(10000, "请求参数错误"),
    NOT_LOGIN_ERROR(10100, "未登录"),
    NO_AUTH_ERROR(10101, "无权限"),
    NOT_FOUND_ERROR(10400, "请求数据不存在"),
    FORBIDDEN_ERROR(10403, "禁止访问"),

    // ===== 服务端错误 =====
    SYSTEM_ERROR(20000, "系统内部异常"),
    OPERATION_ERROR(20001, "操作失败"),

    // ===== 第三方依赖 / 限流 =====
    API_KEY_INVALID(30000, "API Key 无效"),
    API_KEY_DISABLED(30001, "API Key 已被禁用"),
    API_KEY_EXHAUSTED(30002, "API Key 调用次数已用尽"),
    RATE_LIMITED(30003, "请求过于频繁，请稍后重试"),
    AI_SERVICE_ERROR(30100, "AI 服务调用失败"),
    TOOL_EXECUTE_ERROR(30101, "工具执行失败");

    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
