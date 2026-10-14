package org.example.aiagent.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一接口响应包装。
 * <p>
 * 所有 HTTP 接口（SSE 除外）都应返回该类型，避免前端面对多种结构。
 * SSE 接口因为天然是字节流，不走这里，改用 {@link org.example.aiagent.sse.SseEvent} 事件协议。
 *
 * @param <T> 业务数据类型
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private String message;

    private T data;

    public BaseResponse() {
    }

    public BaseResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> BaseResponse<T> success(String message, T data) {
        return new BaseResponse<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    public static <T> BaseResponse<T> success() {
        return success(null);
    }

    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> BaseResponse<T> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), message, null);
    }

    public static <T> BaseResponse<T> error(int code, String message) {
        return new BaseResponse<>(code, message, null);
    }
}
