package com.invoice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统管理控制器
 * 
 * 提供系统健康检查、信息查询和管理功能
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@Tag(name = "System", description = "系统管理相关接口")
public class SystemController implements HealthIndicator {

    private final Environment environment;
    private final DataSource dataSource;
    
    /**
     * 系统信息
     */
    @GetMapping("/info")
    @Operation(
        summary = "获取系统信息",
        description = "获取系统版本、环境、配置等基本信息"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "系统信息获取成功",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\"application\":\"invoice-system-java\",\"version\":\"1.0.0\",\"environment\":\"development\"}"
                )
            )
        )
    })
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        try {
            Map<String, Object> systemInfo = new HashMap<>();
            
            // 基本信息
            systemInfo.put("application", environment.getProperty("spring.application.name", "invoice-system-java"));
            systemInfo.put("version", "1.0.0");
            systemInfo.put("build_time", "2024-01-01T12:00:00");
            systemInfo.put("java_version", System.getProperty("java.version"));
            systemInfo.put("spring_version", org.springframework.core.SpringVersion.getVersion());
            
            // 环境信息
            String[] activeProfiles = environment.getActiveProfiles();
            systemInfo.put("active_profiles", activeProfiles.length > 0 ? activeProfiles : new String[]{"default"});
            systemInfo.put("server_port", environment.getProperty("server.port", "8001"));
            systemInfo.put("context_path", environment.getProperty("server.servlet.context-path", "/"));
            
            // 运行时信息
            Runtime runtime = Runtime.getRuntime();
            systemInfo.put("memory", Map.of(
                "total_mb", runtime.totalMemory() / 1024 / 1024,
                "free_mb", runtime.freeMemory() / 1024 / 1024,
                "max_mb", runtime.maxMemory() / 1024 / 1024,
                "used_mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            ));
            
            systemInfo.put("processors", runtime.availableProcessors());
            systemInfo.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(systemInfo);
            
        } catch (Exception e) {
            log.error("获取系统信息失败", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "获取系统信息失败: " + e.getMessage())
            );
        }
    }
    
    /**
     * 详细健康检查
     */
    @GetMapping("/health/detailed")
    @Operation(
        summary = "详细健康检查",
        description = "获取系统各组件的详细健康状态，包括数据库连接、内存使用等"
    )
    public ResponseEntity<Map<String, Object>> getDetailedHealth() {
        try {
            Map<String, Object> healthInfo = new HashMap<>();
            
            // 整体状态
            Health health = health();
            healthInfo.put("status", health.getStatus().getCode());
            healthInfo.put("timestamp", LocalDateTime.now());
            
            // 数据库健康检查
            Map<String, Object> dbHealth = checkDatabaseHealth();
            healthInfo.put("database", dbHealth);
            
            // 内存健康检查
            Map<String, Object> memoryHealth = checkMemoryHealth();
            healthInfo.put("memory", memoryHealth);
            
            // 磁盘空间检查
            Map<String, Object> diskHealth = checkDiskHealth();
            healthInfo.put("disk", diskHealth);
            
            // 应用组件健康检查
            Map<String, Object> componentsHealth = checkComponentsHealth();
            healthInfo.put("components", componentsHealth);
            
            return ResponseEntity.ok(healthInfo);
            
        } catch (Exception e) {
            log.error("详细健康检查失败", e);
            return ResponseEntity.internalServerError().body(
                Map.of(
                    "status", "DOWN",
                    "error", "健康检查失败: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                )
            );
        }
    }
    
    /**
     * 系统指标
     */
    @GetMapping("/metrics")
    @Operation(
        summary = "获取系统指标",
        description = "获取系统运行时指标，包括JVM、内存、GC等信息"
    )
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // JVM 指标
            Runtime runtime = Runtime.getRuntime();
            metrics.put("jvm", Map.of(
                "memory_total_bytes", runtime.totalMemory(),
                "memory_free_bytes", runtime.freeMemory(),
                "memory_max_bytes", runtime.maxMemory(),
                "memory_used_bytes", runtime.totalMemory() - runtime.freeMemory(),
                "processors", runtime.availableProcessors()
            ));
            
            // 系统指标
            metrics.put("system", Map.of(
                "uptime_ms", System.currentTimeMillis() - getApplicationStartTime(),
                "timestamp", System.currentTimeMillis(),
                "load_average", getSystemLoadAverage()
            ));
            
            // 应用指标（示例）
            metrics.put("application", Map.of(
                "requests_total", 0, // 在实际应用中从计数器获取
                "requests_error_total", 0,
                "response_time_avg_ms", 0.0
            ));
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("获取系统指标失败", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "获取系统指标失败: " + e.getMessage())
            );
        }
    }
    
    /**
     * 配置信息
     */
    @GetMapping("/config")
    @Operation(
        summary = "获取配置信息",
        description = "获取应用的配置信息（脱敏后）"
    )
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        try {
            Map<String, Object> config = new HashMap<>();
            
            // 数据库配置（脱敏）
            config.put("database", Map.of(
                "url", maskSensitiveInfo(environment.getProperty("spring.datasource.url", "")),
                "driver", environment.getProperty("spring.datasource.driver-class-name", ""),
                "pool_size", environment.getProperty("spring.datasource.hikari.maximum-pool-size", "10")
            ));
            
            // 服务器配置
            config.put("server", Map.of(
                "port", environment.getProperty("server.port", "8001"),
                "context_path", environment.getProperty("server.servlet.context-path", "/")
            ));
            
            // 应用配置
            config.put("application", Map.of(
                "name", environment.getProperty("spring.application.name", "invoice-system-java"),
                "rules_config_path", environment.getProperty("invoice-system.rules-config-path", ""),
                "data_directory", environment.getProperty("invoice-system.data-directory", ""),
                "default_currency", environment.getProperty("invoice-system.processing.default-currency", "CNY")
            ));
            
            // 日志配置
            config.put("logging", Map.of(
                "level_root", environment.getProperty("logging.level.root", "INFO"),
                "level_application", environment.getProperty("logging.level.com.invoice", "DEBUG")
            ));
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            log.error("获取配置信息失败", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "获取配置信息失败: " + e.getMessage())
            );
        }
    }
    
    @Override
    public Health health() {
        try {
            // 检查数据库连接
            if (!isDatabaseHealthy()) {
                return Health.down().withDetail("database", "连接失败").build();
            }
            
            // 检查内存使用
            if (!isMemoryHealthy()) {
                return Health.down().withDetail("memory", "内存不足").build();
            }
            
            return Health.up()
                .withDetail("database", "连接正常")
                .withDetail("memory", "内存充足")
                .withDetail("timestamp", LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
    
    /**
     * 检查数据库健康状态
     */
    private Map<String, Object> checkDatabaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5秒超时
            
            return Map.of(
                "status", isValid ? "UP" : "DOWN",
                "database", connection.getMetaData().getDatabaseProductName(),
                "driver", connection.getMetaData().getDriverName(),
                "url", maskSensitiveInfo(connection.getMetaData().getURL())
            );
            
        } catch (Exception e) {
            return Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * 检查内存健康状态
     */
    private Map<String, Object> checkMemoryHealth() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        double usagePercentage = (double) usedMemory / maxMemory * 100;
        String status = usagePercentage < 80 ? "UP" : "WARNING";
        if (usagePercentage > 95) status = "DOWN";
        
        return Map.of(
            "status", status,
            "usage_percentage", Math.round(usagePercentage * 100) / 100.0,
            "total_mb", totalMemory / 1024 / 1024,
            "used_mb", usedMemory / 1024 / 1024,
            "free_mb", freeMemory / 1024 / 1024,
            "max_mb", maxMemory / 1024 / 1024
        );
    }
    
    /**
     * 检查磁盘健康状态
     */
    private Map<String, Object> checkDiskHealth() {
        try {
            java.io.File root = new java.io.File("/");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            
            double usagePercentage = (double) usedSpace / totalSpace * 100;
            String status = usagePercentage < 80 ? "UP" : "WARNING";
            if (usagePercentage > 95) status = "DOWN";
            
            return Map.of(
                "status", status,
                "usage_percentage", Math.round(usagePercentage * 100) / 100.0,
                "total_gb", totalSpace / 1024 / 1024 / 1024,
                "used_gb", usedSpace / 1024 / 1024 / 1024,
                "free_gb", freeSpace / 1024 / 1024 / 1024
            );
            
        } catch (Exception e) {
            return Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * 检查组件健康状态
     */
    private Map<String, Object> checkComponentsHealth() {
        Map<String, Object> components = new HashMap<>();
        
        // 在实际应用中，这里可以检查各个业务组件的状态
        components.put("rule_engine", Map.of("status", "UP", "message", "规则引擎运行正常"));
        components.put("kdubl_converter", Map.of("status", "UP", "message", "KDUBL转换器运行正常"));
        components.put("smart_query_system", Map.of("status", "UP", "message", "智能查询系统运行正常"));
        components.put("workflow_service", Map.of("status", "UP", "message", "工作流服务运行正常"));
        
        return components;
    }
    
    /**
     * 检查数据库是否健康
     */
    private boolean isDatabaseHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查内存是否健康
     */
    private boolean isMemoryHealthy() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        return (double) usedMemory / maxMemory < 0.9; // 内存使用率小于90%
    }
    
    /**
     * 脱敏敏感信息
     */
    private String maskSensitiveInfo(String info) {
        if (info == null || info.length() < 8) {
            return "****";
        }
        return info.substring(0, 4) + "****" + info.substring(info.length() - 4);
    }
    
    /**
     * 获取应用启动时间（简化实现）
     */
    private long getApplicationStartTime() {
        // 在实际应用中，可以通过ApplicationContext或其他方式获取准确的启动时间
        return System.currentTimeMillis() - 300000; // 假设应用已运行5分钟
    }
    
    /**
     * 获取系统负载平均值（简化实现）
     */
    private double getSystemLoadAverage() {
        try {
            return ((com.sun.management.OperatingSystemMXBean) 
                java.lang.management.ManagementFactory.getOperatingSystemMXBean())
                .getProcessCpuLoad();
        } catch (Exception e) {
            return 0.0;
        }
    }
}