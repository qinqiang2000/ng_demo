package com.invoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 下一代开票系统 Java 后端主应用类
 * 
 * 功能等价于 Python FastAPI 后端，提供：
 * - KDUBL 发票处理
 * - CEL 规则引擎
 * - 智能字段补全
 * - 业务校验
 * - 数据库集成
 */
@SpringBootApplication
@EnableConfigurationProperties
public class InvoiceSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceSystemApplication.class, args);
    }
}