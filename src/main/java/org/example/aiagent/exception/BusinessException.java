package org.example.aiagent.exception;

import lombok.Getter;
import org.example.aiagent.common.ErrorCode;

/**
 * 业务异常。
 * <p>
 * 与 {@link RuntimeException} 的区别：业务异常是「可预期」的失败（参数错误、无权限、Key 用尽等），
 * 全局异常处理器会把它转成对应错误码返回给前端；而未知异常一律按系统错误处理并记录完整堆栈。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
