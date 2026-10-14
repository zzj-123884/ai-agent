package org.example.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.common.ErrorCode;
import org.example.aiagent.common.Validator;
import org.example.aiagent.exception.BusinessException;
import org.example.aiagent.model.dto.user.UserLoginRequest;
import org.example.aiagent.model.dto.user.UserRegisterRequest;
import org.example.aiagent.model.entity.User;
import org.example.aiagent.service.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 用户服务实现。
 * <p>
 * 密码哈希说明：这里没有引入 Spring Security，因此用 JDK 自带的 PBKDF2
 * （{@link javax.crypto.SecretKeyFactory}）而不是 BCrypt。
 * <p>
 * 为什么不用裸 SHA-256：用户密码是低熵的人类输入，裸哈希可被彩虹表直接反查。
 * PBKDF2 是标准 KDF：加随机盐 + 高迭代次数，专门用于抵抗离线暴力破解，
 * 且 JDK 自带、零额外依赖。若后续引入 Spring Security，替换成 BCryptPasswordEncoder 即可，
 * 对外接口不变。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final int ITERATIONS = 100_000;

    private static final int KEY_LENGTH = 256;

    private static final int SALT_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final RowMapper<User> ROW_MAPPER = new RowMapper<>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUserAccount(rs.getString("user_account"));
            user.setUserName(rs.getString("user_name"));
            user.setUserPassword(rs.getString("user_password"));
            user.setUserAvatar(rs.getString("user_avatar"));
            user.setUserRole(rs.getString("user_role"));
            user.setStatus(rs.getInt("status"));
            Timestamp createTime = rs.getTimestamp("create_time");
            user.setCreateTime(createTime == null ? null : createTime.toLocalDateTime());
            Timestamp updateTime = rs.getTimestamp("update_time");
            user.setUpdateTime(updateTime == null ? null : updateTime.toLocalDateTime());
            return user;
        }
    };

    @Override
    public long register(UserRegisterRequest request) {
        Validator.requireNonNull(request, "请求体");
        Validator.requireLength(request.getUserAccount(), "账号", 4, 32);
        Validator.requireLength(request.getUserPassword(), "密码", 8, 64);

        // 两次密码一致性：放在服务端校验，前端校验可被绕过
        if (!request.getUserPassword().equals(request.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        // 账号唯一性：先查后插在并发下仍可能重复，因此数据库层面必须加唯一索引兜底
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE user_account = ?",
                Long.class, request.getUserAccount());
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }

        String encrypted = encryptPassword(request.getUserPassword());
        String userName = StrUtil.isBlank(request.getUserName())
                ? "用户" + request.getUserAccount()
                : request.getUserName();

        jdbcTemplate.update("""
                INSERT INTO user_account
                    (user_account, user_name, user_password, user_role, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                request.getUserAccount(), userName, encrypted, "user", 0,
                Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM user_account WHERE user_account = ?",
                Long.class, request.getUserAccount());
        log.info("新用户注册成功: {}", request.getUserAccount());
        return id == null ? -1L : id;
    }

    @Override
    public User login(UserLoginRequest request) {
        Validator.requireNonNull(request, "请求体");
        Validator.requireLength(request.getUserAccount(), "账号", 1, 32);
        Validator.requireLength(request.getUserPassword(), "密码", 1, 64);

        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user_account WHERE user_account = ?",
                ROW_MAPPER, request.getUserAccount());

        if (users.isEmpty()) {
            // 账号不存在与密码错误返回同一句提示，避免被用来枚举有效账号
            log.warn("登录失败，账号不存在: {}", request.getUserAccount());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }

        User user = users.get(0);
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号已被封禁");
        }
        if (!verifyPassword(request.getUserPassword(), user.getUserPassword())) {
            log.warn("登录失败，密码错误: {}", request.getUserAccount());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        return getSafeUser(user.getId());
    }

    @Override
    public User getSafeUser(Long userId) {
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user_account WHERE id = ?", ROW_MAPPER, userId);
        if (users.isEmpty()) {
            return null;
        }
        User user = users.get(0);
        // 脱敏：密码哈希绝不能出接口
        user.setUserPassword(null);
        return user;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && "admin".equals(user.getUserRole());
    }

    /**
     * 加密密码，返回 {@code base64(salt):base64(hash)} 格式。
     * <p>
     * 把盐与哈希拼在同一列存储，避免为了加一列盐而改表结构；
     * 校验时按冒号切分还原即可。
     */
    private String encryptPassword(String plainPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(plainPassword, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 校验密码。
     */
    private boolean verifyPassword(String plainPassword, String storedPassword) {
        if (StrUtil.isBlank(storedPassword) || !storedPassword.contains(":")) {
            return false;
        }
        String[] parts = storedPassword.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual = pbkdf2(plainPassword, salt);
        // 常量时间比较，防止通过响应时间差逐字节猜出哈希
        return MessageDigest.isEqual(actual, expected);
    }

    private byte[] pbkdf2(String password, byte[] salt) {
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            javax.crypto.SecretKeyFactory factory =
                    javax.crypto.SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            // 算法不可用属于运行环境故障，必须让上层感知而不是返回“密码正确”
            throw new IllegalStateException("密码加密失败", e);
        }
    }
}
