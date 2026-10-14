package org.example.aiagent.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.common.BaseResponse;
import org.example.aiagent.common.ErrorCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理器。
 * <p>
 * 核心价值：Controller 里不再需要 try-catch 兜底，业务代码只管抛异常，
 * 由这里统一转成 {@link BaseResponse}，保证前端拿到的永远是同一种结构。
 * <p>
 * 分级策略：
 * <ul>
 *   <li>业务异常（BusinessException）—— 可预期，warn 级别，不回堆栈</li>
 *   <li>参数异常 —— 客户端问题，warn 级别，返回 10000</li>
 *   <li>未知异常 —— error 级别，记录完整堆栈，对外只暴露笼统文案（避免泄漏内部实现）</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getCode(), e.getMessage());
        return BaseResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验工具抛出的异常。
     * 单独处理它而不是归入 BusinessException，是为了让 common 包不依赖 exception 包。
     */
    @ExceptionHandler(org.example.aiagent.common.Validator.BusinessCheckException.class)
    public BaseResponse<?> businessCheckExceptionHandler(
            org.example.aiagent.common.Validator.BusinessCheckException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return BaseResponse.error(ErrorCode.PARAMS_ERROR, e.getMessage());
    }

    /**
     * @Valid / @Validated 校验失败。
     * 只取第一条错误提示，因为前端一次只展示一个提示，返回整列表反而要前端再挑。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ErrorCode.PARAMS_ERROR.getMessage());
        log.warn("参数校验失败: {}", message);
        return BaseResponse.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public BaseResponse<?> constraintViolationExceptionHandler(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .orElse(ErrorCode.PARAMS_ERROR.getMessage());
        log.warn("参数校验失败: {}", message);
        return BaseResponse.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public BaseResponse<?> missingServletRequestParameterExceptionHandler(MissingServletRequestParameterException e) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn(message);
        return BaseResponse.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return BaseResponse.error(ErrorCode.PARAMS_ERROR, "请求体格式错误");
    }

    /**
     * 静态资源 404。Spring Boot 3.2+ 会抛出该异常而不是返回 404，
     * 若不单独处理，用户访问不存在的路径会看到「系统内部异常」，误导性很强。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public BaseResponse<?> noResourceFoundExceptionHandler(NoResourceFoundException e) {
        log.warn("请求的资源不存在: {}", e.getResourcePath());
        return BaseResponse.error(ErrorCode.NOT_FOUND_ERROR);
    }

    /**
     * SSE 连接被客户端主动断开时抛出。
     * 这不是错误——用户点了「停止」或者关掉了页面，属于正常流程，只记 debug。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncRequestNotUsableExceptionHandler(AsyncRequestNotUsableException e) {
        log.debug("SSE 连接已被客户端关闭: {}", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("运行时异常", e);
        return BaseResponse.error(ErrorCode.SYSTEM_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<?> exceptionHandler(Exception e) {
        log.error("系统异常", e);
        return BaseResponse.error(ErrorCode.SYSTEM_ERROR);
    }
}
