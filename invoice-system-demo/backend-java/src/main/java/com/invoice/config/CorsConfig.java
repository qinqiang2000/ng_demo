package com.invoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS 配置类
 * 
 * 配置跨域访问规则，与 Python FastAPI 后端保持一致
 * 允许前端应用从 localhost:3000 访问 API
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 全局 CORS 配置
     * 
     * 与 Python 后端的 CORS 配置保持一致：
     * - 允许 localhost:3000 和 127.0.0.1:3000 访问
     * - 支持所有 HTTP 方法
     * - 允许所有请求头
     * - 支持凭据传递
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                    "http://localhost:3000",
                    "http://127.0.0.1:3000"
                )
                .allowedMethods(
                    "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
                )
                .allowedHeaders(
                    "Content-Type", "Authorization", "X-Requested-With", 
                    "Accept", "Origin", "Access-Control-Request-Method", 
                    "Access-Control-Request-Headers"
                )
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * CORS 配置源 Bean
     * 
     * 为 Spring Security 或其他组件提供 CORS 配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 允许的源
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://127.0.0.1:3000"
        ));
        
        // 允许的 HTTP 方法
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // 允许的请求头
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type", "Authorization", "X-Requested-With", 
            "Accept", "Origin", "Access-Control-Request-Method", 
            "Access-Control-Request-Headers"
        ));
        
        // 允许凭据
        configuration.setAllowCredentials(false);
        
        // 预检请求的缓存时间
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}