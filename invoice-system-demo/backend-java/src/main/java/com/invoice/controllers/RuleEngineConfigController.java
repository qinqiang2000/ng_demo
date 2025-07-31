package com.invoice.controllers;

import com.invoice.config.RuleEngineConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 规则引擎配置控制器
 * 提供运行时配置和管理规则引擎的API
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "规则引擎配置", description = "管理规则引擎配置的API")
public class RuleEngineConfigController {
    
    private final RuleEngineConfigService configService;
    
    /**
     * 获取当前规则引擎配置
     */
    @GetMapping("/rule-engine")
    @Operation(summary = "获取当前规则引擎配置", description = "返回当前使用的规则引擎及相关配置信息")
    public ResponseEntity<Map<String, Object>> getCurrentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("current_engine", configService.getCurrentEngine());
        config.put("default_engine", configService.getDefaultEngine());
        config.put("allow_runtime_switch", configService.isAllowRuntimeSwitch());
        config.put("is_currently_spel", configService.isCurrentlySpel());
        config.put("is_currently_cel", configService.isCurrentlyCel());
        config.put("success", true);
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * 设置当前使用的规则引擎
     */
    @PostMapping("/rule-engine")
    @Operation(summary = "设置当前规则引擎", description = "设置系统当前使用的规则引擎类型")
    public ResponseEntity<Map<String, Object>> setCurrentEngine(
            @Parameter(description = "规则引擎类型", example = "spel")
            @RequestBody Map<String, String> request) {
        
        String engine = request.get("engine");
        if (engine == null || engine.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("ENGINE_REQUIRED", "必须指定规则引擎类型"));
        }
        
        engine = engine.trim().toLowerCase();
        if (!"spel".equals(engine) && !"cel".equals(engine)) {
            return ResponseEntity.badRequest().body(createErrorResponse("INVALID_ENGINE", "无效的规则引擎类型，只支持 'spel' 或 'cel'"));
        }
        
        boolean success = configService.setCurrentEngine(engine);
        if (!success) {
            if (!configService.isAllowRuntimeSwitch()) {
                return ResponseEntity.badRequest().body(createErrorResponse("RUNTIME_SWITCH_DISABLED", "运行时切换引擎被禁用"));
            } else {
                return ResponseEntity.badRequest().body(createErrorResponse("SET_ENGINE_FAILED", "设置规则引擎失败"));
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "规则引擎已成功设置为: " + engine);
        response.put("previous_engine", request.get("previous_engine"));
        response.put("current_engine", configService.getCurrentEngine());
        
        log.info("规则引擎配置已更新为: {}", engine);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 重置为默认规则引擎
     */
    @PostMapping("/rule-engine/reset")
    @Operation(summary = "重置为默认规则引擎", description = "将当前规则引擎重置为配置文件中的默认值")
    public ResponseEntity<Map<String, Object>> resetToDefault() {
        if (!configService.isAllowRuntimeSwitch()) {
            return ResponseEntity.badRequest().body(createErrorResponse("RUNTIME_SWITCH_DISABLED", "运行时切换引擎被禁用"));
        }
        
        String previousEngine = configService.getCurrentEngine();
        configService.resetToDefault();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "规则引擎已重置为默认值: " + configService.getDefaultEngine());
        response.put("previous_engine", previousEngine);
        response.put("current_engine", configService.getCurrentEngine());
        
        log.info("规则引擎配置已重置为默认值: {}", configService.getDefaultEngine());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取支持的规则引擎列表
     */
    @GetMapping("/rule-engine/supported")
    @Operation(summary = "获取支持的规则引擎列表", description = "返回系统支持的所有规则引擎及其详细信息")
    public ResponseEntity<Map<String, Object>> getSupportedEngines() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("current_engine", configService.getCurrentEngine());
        response.put("default_engine", configService.getDefaultEngine());
        response.put("allow_runtime_switch", configService.isAllowRuntimeSwitch());
        
        // 支持的引擎列表
        Map<String, Object> spelEngine = new HashMap<>();
        spelEngine.put("name", "spel");
        spelEngine.put("display_name", "Spring Expression Language");
        spelEngine.put("description", "基于Spring表达式语言的规则引擎，支持复杂的表达式计算");
        spelEngine.put("features", new String[]{"复杂表达式计算", "丰富的内置函数", "类型安全", "详细的执行日志"});
        spelEngine.put("is_current", configService.isCurrentlySpel());
        spelEngine.put("is_default", "spel".equals(configService.getDefaultEngine()));
        
        Map<String, Object> celEngine = new HashMap<>();
        celEngine.put("name", "cel");
        celEngine.put("display_name", "Common Expression Language");
        celEngine.put("description", "Google开发的通用表达式语言，性能优异");
        celEngine.put("features", new String[]{"高性能执行", "类型安全", "简洁的语法", "跨平台支持"});
        celEngine.put("is_current", configService.isCurrentlyCel());
        celEngine.put("is_default", "cel".equals(configService.getDefaultEngine()));
        
        response.put("engines", new Object[]{spelEngine, celEngine});
        response.put("total_engines", 2);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String errorCode, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error_code", errorCode);
        error.put("message", message);
        error.put("current_engine", configService.getCurrentEngine());
        return error;
    }
}