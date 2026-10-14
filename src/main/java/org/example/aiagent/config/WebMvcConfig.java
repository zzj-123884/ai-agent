package org.example.aiagent.config;

import org.example.aiagent.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

/**
 * Web MVC 配置：注册鉴权拦截器（P2）。
 * <p>
 * 拦截范围的选择很关键：
 * <ul>
 *   <li>只拦 {@code /ai/**} —— 这是烧钱的大模型接口，也是本次要保护的资产；</li>
 *   <li>放行 {@code /user/**} —— 登录接口本身不能被鉴权拦住，否则永远登不进去；</li>
 *   <li>放行 {@code /api_key/**} 中的签发/查询接口需要登录，因此**不**放行，
 *       它们依赖 session 身份，走同一个拦截器即可；</li>
 *   <li>放行文档与静态资源 —— 否则 Swagger / Knife4j 页面打不开，
 *       调试体验会明显变差。生产环境应通过配置项关闭文档而非在此处纠结。</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/ai/**", "/api-key/**", "/v1/**")
                .excludePathPatterns(
                        // 登录相关，必须匿名可访问
                        "/user/login",
                        "/user/register",
                        "/user/get/login",
                        // 健康检查，供容器/网关探活
                        "/health/**",
                        "/error",
                        // 接口文档。当前 addPathPatterns 只含 /ai/** 与 /api-key/**，
                        // 文档本就不在拦截范围内；这里显式列出是为了将来把范围扩到 /**
                        // （例如做全站鉴权）时不会漏掉，否则文档页会变成 10100 白屏。
                        // 生产环境应该用 knife4j.production=true 彻底关掉文档，
                        // 而不是依赖“拦得住”——因为 /v3/api-docs 仍能被直接请求。
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/webjars/**",
                        "/favicon.ico"
                );
    }
}
