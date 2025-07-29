package com.invoice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置
 * 
 * 配置 Swagger UI 和 API 文档生成
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI invoiceSystemOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Invoice System API")
                .description("新一代发票处理系统 - Java Spring Boot 后端 API 接口文档")
                .version("v1.0.0")
                .contact(new Contact()
                    .name("Invoice System Team")
                    .email("support@invoicesystem.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8000")
                    .description("开发环境服务器"),
                new Server()
                    .url("http://localhost:8001")
                    .description("Java 后端服务器")))
            .tags(List.of(
                new Tag()
                    .name("Invoice Processing")
                    .description("发票处理相关接口"),
                new Tag()
                    .name("Workflow")
                    .description("工作流编排相关接口"),
                new Tag()
                    .name("System")
                    .description("系统管理相关接口"),
                new Tag()
                    .name("Health & Monitoring")
                    .description("健康检查和监控相关接口")));
    }
}