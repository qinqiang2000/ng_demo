package com.invoice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则引擎配置服务
 * 管理当前使用的规则引擎类型
 */
@Slf4j
@Service
public class RuleEngineConfigService {
    
    @Value("${invoice-system.rule-engine.default:spel}")
    private String defaultEngine;
    
    @Value("${invoice-system.rule-engine.allow-runtime-switch:true}")
    private boolean allowRuntimeSwitch;
    
    // 当前使用的引擎，使用原子引用保证线程安全
    private final AtomicReference<String> currentEngine = new AtomicReference<>();
    
    /**
     * 获取当前使用的规则引擎
     */
    public String getCurrentEngine() {
        String current = currentEngine.get();
        if (current == null) {
            // 如果没有设置，使用默认引擎
            current = defaultEngine;
            currentEngine.set(current);
        }
        return current;
    }
    
    /**
     * 设置当前使用的规则引擎
     */
    public boolean setCurrentEngine(String engine) {
        if (!allowRuntimeSwitch) {
            log.warn("运行时切换引擎被禁用，无法设置引擎为: {}", engine);
            return false;
        }
        
        if (!isValidEngine(engine)) {
            log.warn("无效的规则引擎类型: {}", engine);
            return false;
        }
        
        String oldEngine = currentEngine.get();
        currentEngine.set(engine);
        log.info("规则引擎已从 {} 切换到 {}", oldEngine, engine);
        return true;
    }
    
    /**
     * 重置为默认引擎
     */
    public void resetToDefault() {
        String oldEngine = currentEngine.get();
        currentEngine.set(defaultEngine);
        log.info("规则引擎已重置为默认值: {} (原值: {})", defaultEngine, oldEngine);
    }
    
    /**
     * 检查是否为有效的引擎类型
     */
    private boolean isValidEngine(String engine) {
        return "spel".equals(engine) || "cel".equals(engine);
    }
    
    /**
     * 获取默认引擎
     */
    public String getDefaultEngine() {
        return defaultEngine;
    }
    
    /**
     * 是否允许运行时切换
     */
    public boolean isAllowRuntimeSwitch() {
        return allowRuntimeSwitch;
    }
    
    /**
     * 检查当前是否使用 SpEL 引擎
     */
    public boolean isCurrentlySpel() {
        return "spel".equals(getCurrentEngine());
    }
    
    /**
     * 检查当前是否使用 CEL 引擎
     */
    public boolean isCurrentlyCel() {
        return "cel".equals(getCurrentEngine());
    }
}