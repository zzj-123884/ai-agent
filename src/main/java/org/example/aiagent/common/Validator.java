package org.example.aiagent.common;

import cn.hutool.core.util.StrUtil;

/**
 * 参数校验工具。
 * <p>
 * 集中放置的原因：Controller 里会有大量重复的「非空 / 长度 / 范围」检查，
 * 散落各处时容易漏检，且修改规则要全局搜索。收敛到这里后规则只有一份。
 */
public final class Validator {

    private Validator() {
    }

    /**
     * 校验字符串非空且长度在 [min, max] 内。
     */
    public static void requireLength(String value, String fieldName, int min, int max) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessCheckException(fieldName + " 不能为空");
        }
        if (value.length() < min || value.length() > max) {
            throw new BusinessCheckException(fieldName + " 长度需在 " + min + " 到 " + max + " 之间");
        }
    }

    /**
     * 校验对象非空。
     */
    public static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessCheckException(fieldName + " 不能为空");
        }
    }

    /**
     * 校验 id 为正数。
     */
    public static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BusinessCheckException(fieldName + " 不合法");
        }
    }

    /**
     * 参数校验失败的轻量异常，由全局异常处理器映射为 PARAMS_ERROR。
     * <p>
     * 继承 RuntimeException 而非 BusinessException 是为了避免 common 包反向依赖 exception 包，
     * 处理器里单独捕获它即可。
     */
    public static class BusinessCheckException extends RuntimeException {
        public BusinessCheckException(String message) {
            super(message);
        }
    }
}
