package org.example.aiagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.auth.AuthContext;
import org.example.aiagent.common.BaseResponse;
import org.example.aiagent.common.ErrorCode;
import org.example.aiagent.exception.BusinessException;
import org.example.aiagent.model.entity.UserApiKey;
import org.example.aiagent.service.ApiKeyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.io.Serializable;
import java.util.List;

/**
 * API Key 管理接口（P2：开放平台接入与计费）。
 * <p>
 * 全部接口要求 session 登录（由 AuthInterceptor 保护）—— 因为需要「当前用户」身份来归属 Key。
 * 第三方应用拿着签发的 key/secret 去调 /ai/** 时不需要 session。
 * <p>
 * 安全约定：secretKey 明文只在创建响应里出现一次，之后任何接口都不再返回。
 * 用户如果遗失，只能删除重建。
 */
@Slf4j
@RestController
@RequestMapping("/api-key")
@Tag(name = "API Key 管理模块")
public class ApiKeyController {

    @Resource
    private ApiKeyService apiKeyService;

    @PostMapping("/create")
    @Operation(summary = "签发 API Key（secretKey 仅返回一次）")
    public BaseResponse<CreateApiKeyResponse> createApiKey(@RequestBody CreateApiKeyRequest request) {
        Long userId = requireLoginUserId();

        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Key 名称不能为空");
        }
        if (request.getName().length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Key 名称过长");
        }
        // 配额必须为正数；null 表示不限，负数是非法输入
        if (request.getTotalQuota() != null && request.getTotalQuota() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配额必须大于 0");
        }

        UserApiKey key = apiKeyService.createApiKey(userId, request.getName(), request.getTotalQuota());

        CreateApiKeyResponse response = new CreateApiKeyResponse();
        response.setId(key.getId());
        response.setName(key.getName());
        response.setTotalQuota(key.getTotalQuota());
        // 明文仅此一次
        response.setSecretKey(key.getPlainSecretKey());
        response.setToken(key.getPlainSecretKey());
        response.setTip("请立即保存 token，之后无法再次查看。调用时放在 Authorization: Bearer <token>");
        return BaseResponse.success(response);
    }

    @GetMapping("/list")
    @Operation(summary = "查询我的 API Key 列表")
    public BaseResponse<List<UserApiKey>> listApiKeys() {
        Long userId = requireLoginUserId();
        List<UserApiKey> keys = apiKeyService.listByUserId(userId);
        // 双重保险：即使实现层有所遗漏，这里也强制抹掉哈希，绝不外泄
        keys.forEach(key -> {
            key.setSecretKeyHash(null);
            key.setAccessKeyHash(null);
        });
        return BaseResponse.success(keys);
    }

    @PostMapping("/status")
    @Operation(summary = "启用/禁用 API Key")
    public BaseResponse<Boolean> updateStatus(
            @RequestParam Long apiKeyId,
            @RequestParam int status) {
        Long userId = requireLoginUserId();
        if (status != 0 && status != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "status 只能为 0 或 1");
        }
        boolean updated = apiKeyService.updateStatus(apiKeyId, userId, status);
        if (!updated) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Key 不存在或无权限");
        }
        return BaseResponse.success(true);
    }

    @DeleteMapping("/{apiKeyId}")
    @Operation(summary = "删除 API Key")
    public BaseResponse<Boolean> deleteApiKey(@PathVariable Long apiKeyId) {
        Long userId = requireLoginUserId();
        boolean deleted = apiKeyService.deleteApiKey(apiKeyId, userId);
        if (!deleted) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Key 不存在或无权限");
        }
        return BaseResponse.success(true);
    }

    /**
     * 从上下文取当前用户。
     * <p>
     * 虽然拦截器已保证有身份，但上下文可能因配置疏漏而为空，
     * 因此这里再断言一次，避免用 null 作为 userId 去查库得到空列表（静默出错）。
     */
    private Long requireLoginUserId() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return userId;
    }

    /**
     * 签发请求。
     */
    @Data
    public static class CreateApiKeyRequest implements Serializable {
        private String name;
        private Long totalQuota;
    }

    /**
     * 签发响应。刻意与实体分开：只暴露必要字段，防止哈希被误带出去。
     */
    @Data
    public static class CreateApiKeyResponse implements Serializable {
        private Long id;
        private String name;
        private Long totalQuota;
        private String secretKey;
        private String token;
        private String tip;
    }
}
