package org.example.aiagent.exception;

import lombok.Getter;
import org.example.aiagent.common.ErrorCode;

/**
 * 抛出该异常即可让全局异常处理器返回「未登录」错误码。
 * <p>
 * 之所以单独建一个类，是因为登录态判断散落在拦截器和 Controller 中，
 * 统一类型后前端可以用 10100 精确识别并跳转登录页，而不是笼统的「系统错误」。
 */
@Getter
public class NotLoginException extends BusinessException {

    public NotLoginException() {
        super(ErrorCode.NOT_LOGIN_ERROR);
    }

    public NotLoginException(String message) {
        super(ErrorCode.NOT_LOGIN_ERROR, message);
    }
}
