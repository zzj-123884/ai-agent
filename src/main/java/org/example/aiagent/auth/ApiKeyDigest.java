package org.example.aiagent.auth;

/**
 * API Key 摘要工具。
 * <p>
 * 用 SHA-256 而非 BCrypt 的原因：API Key 是**高熵随机串**（32+ 字符），
 * 不存在被字典/暴力破解的现实风险，因此不需要 BCrypt 的慢哈希与加盐。
 * 而 SHA-256 的优势是**可确定性查询** —— 能直接拿哈希去数据库 where 匹配，
 * BCrypt 因为每次盐不同，只能全表扫描逐条 compare，性能差距是数量级的。
 * <p>
 * （用户密码则用 PBKDF2 加盐，因为密码是低熵的人类输入。）
 */
public final class ApiKeyDigest {

    private ApiKeyDigest() {
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     */
    public static String sha256(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String s = Integer.toHexString(0xff & b);
                if (s.length() == 1) {
                    hex.append('0');
                }
                hex.append(s);
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 保证存在的算法，走到这里说明运行环境异常
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 常量时间比较，避免通过响应时间差推测哈希前缀（时序攻击）。
     */
    public static boolean matches(String raw, String hashed) {
        if (raw == null || hashed == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                sha256(raw).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                hashed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
