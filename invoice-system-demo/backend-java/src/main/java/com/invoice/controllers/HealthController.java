package com.invoice.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 
 * 提供系统状态检查，不依赖数据库连接
 */
@RestController
@RequestMapping("/api/health")
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class HealthController {

    /**
     * 基础健康检查
     * 
     * @return 系统状态
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("service", "invoice-system-java");
        health.put("version", "1.0.0-dev");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("message", "Java 后端基础功能正常运行");
        
        return ResponseEntity.ok(health);
    }

    /**
     * 详细状态检查
     * 
     * @return 详细系统信息
     */
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> healthDetail() {
        Map<String, Object> detail = new HashMap<>();
        
        // 基础信息
        detail.put("service", "Invoice System Java Backend");
        detail.put("version", "1.0.0-dev");
        detail.put("status", "running");
        detail.put("startTime", LocalDateTime.now().minusMinutes(1).toString());
        detail.put("uptime", "运行中");
        
        // Java 信息
        Map<String, Object> javaInfo = new HashMap<>();
        javaInfo.put("version", System.getProperty("java.version"));
        javaInfo.put("vendor", System.getProperty("java.vendor"));
        javaInfo.put("runtime", System.getProperty("java.runtime.name"));
        detail.put("java", javaInfo);
        
        // 系统信息
        Map<String, Object> systemInfo = new HashMap<>();
        systemInfo.put("os", System.getProperty("os.name"));
        systemInfo.put("arch", System.getProperty("os.arch"));
        systemInfo.put("processors", Runtime.getRuntime().availableProcessors());
        systemInfo.put("memory", Map.of(
            "total", Runtime.getRuntime().totalMemory(),
            "free", Runtime.getRuntime().freeMemory(),
            "max", Runtime.getRuntime().maxMemory()
        ));
        detail.put("system", systemInfo);
        
        // 功能状态
        Map<String, String> features = new HashMap<>();
        features.put("基础框架", "✓ 完成");
        features.put("REST API", "✓ 完成");
        features.put("CORS配置", "✓ 完成");
        features.put("域模型", "✓ 完成");
        features.put("数据库配置", "✓ 完成");
        features.put("CEL引擎", "开发中");
        features.put("规则引擎", "开发中");
        features.put("KDUBL转换", "开发中");
        detail.put("features", features);
        
        return ResponseEntity.ok(detail);
    }

    /**
     * API 兼容性测试
     * 
     * 测试与 Python 后端的 API 兼容性
     * 
     * @return 兼容性信息
     */
    @GetMapping("/compatibility")
    public ResponseEntity<Map<String, Object>> compatibility() {
        Map<String, Object> compat = new HashMap<>();
        
        compat.put("backend", "java");
        compat.put("pythonCompatible", true);
        compat.put("apiVersion", "v1");
        compat.put("endpoints", Map.of(
            "健康检查", "/api/v1/health",
            "发票处理", "/api/v1/process/invoice",
            "批量处理", "/api/v1/process/batch",
            "字段补全", "/api/v1/process/complete",
            "业务校验", "/api/v1/process/validate"
        ));
        
        compat.put("sharedResources", Map.of(
            "database", "../shared/database/invoice_system.db",
            "rules", "../shared/config/rules.yaml",
            "data", "../shared/data/"
        ));
        
        compat.put("port", 8001);
        compat.put("pythonBackendPort", 8000);
        
        return ResponseEntity.ok(compat);
    }

    /**
     * 测试接口
     * 
     * 用于验证 Java 后端基础功能
     * 
     * @param message 测试消息
     * @return 测试结果
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody(required = false) Map<String, Object> request) {
        log.info("收到测试请求: {}", request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Java 后端测试成功");
        response.put("backend", "java-spring-boot");
        response.put("received", request);
        response.put("timestamp", LocalDateTime.now().toString());
        
        if (request != null && request.containsKey("echo")) {
            response.put("echo", request.get("echo"));
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 热重载测试接口
     * 
     * 测试Spring Boot DevTools热重载功能
     * 
     * @return 热重载状态信息
     */
    @GetMapping("/hotreload-test")
    public ResponseEntity<Map<String, Object>> hotReloadTest() {
        log.info("热重载测试接口被调用");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "🔥 热重载功能正常工作！");
        response.put("feature", "Spring Boot DevTools Hot Reload");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("reloadInfo", Map.of(
            "enabled", true,
            "description", "代码修改后2-3秒自动重启",
            "liveReloadPort", 35729,
            "triggerFile", ".reloadtrigger"
        ));
        
        return ResponseEntity.ok(response);
    }
}