package com.invoice.controllers;

import com.invoice.core.RuleEngine;
import com.invoice.spel.SpelRuleEngine;
import com.invoice.config.RuleEngineConfigService;
import com.invoice.models.BusinessRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则管理控制器
 * 
 * 提供规则配置的API接口
 * 与 Python 版本功能完全等价
 */
@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@RequiredArgsConstructor
@Slf4j
public class RulesController {

    private final RuleEngine ruleEngine;
    private final SpelRuleEngine spelRuleEngine;
    private final RuleEngineConfigService ruleEngineConfigService;

    /**
     * 获取所有规则
     * 
     * @return 规则列表和统计信息
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRules() {
        try {
            log.info("获取规则列表，当前引擎: {}", ruleEngineConfigService.getCurrentEngine());
            
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> completionRules = new ArrayList<>();
            List<Map<String, Object>> validationRules = new ArrayList<>();
            Map<String, Object> stats;
            
            // 根据当前引擎类型获取规则
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 使用 SpEL 引擎
                spelRuleEngine.loadRules("../shared/config/rules_spel.yaml");
                stats = spelRuleEngine.getRuleStats();
                
                // 获取 SpEL 规则并分类
                List<BusinessRule> spelCompletionRules = spelRuleEngine.getCompletionRules();
                List<BusinessRule> spelValidationRules = spelRuleEngine.getValidationRules();
                
                completionRules = spelCompletionRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
                validationRules = spelValidationRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
            } else {
                // 使用 CEL 引擎
                ruleEngine.loadRules("../shared/config/rules.yaml");
                stats = ruleEngine.getRuleStats();
                
                // 获取 CEL 规则并分类
                List<RuleEngine.CompletionRule> celCompletionRules = ruleEngine.getCompletionRules();
                List<RuleEngine.ValidationRule> celValidationRules = ruleEngine.getValidationRules();
                
                completionRules = celCompletionRules.stream()
                    .map(this::convertCompletionRuleToMap)
                    .collect(Collectors.toList());
                validationRules = celValidationRules.stream()
                    .map(this::convertValidationRuleToMap)
                    .collect(Collectors.toList());
            }
            
            // 构建前端期望的数据结构
            Map<String, Object> data = new HashMap<>();
            data.put("completion_rules", completionRules);
            data.put("validation_rules", validationRules);
            data.put("totalRules", stats.get("rules_loaded"));
            data.put("completionRulesCount", stats.get("completion_rules_count"));
            data.put("validationRulesCount", stats.get("validation_rules_count"));
            data.put("activeRules", stats.get("active_rules"));
            data.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            // 返回前端期望的响应格式
            response.put("success", true);
            response.put("data", data);
            response.put("message", "规则列表获取成功");
            
            log.info("规则列表获取完成 - 引擎: {}, 总数: {}, 补全: {}, 验证: {}", 
                ruleEngineConfigService.getCurrentEngine(),
                stats.get("rules_loaded"), 
                stats.get("completion_rules_count"), 
                stats.get("validation_rules_count"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "获取规则列表失败: " + e.getMessage());
            errorResponse.put("message", "获取规则列表失败");
            errorResponse.put("engine", ruleEngineConfigService.getCurrentEngine());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 将 BusinessRule 转换为 Map 格式
     */
    private Map<String, Object> convertBusinessRuleToMap(BusinessRule rule) {
        Map<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("id", rule.getRuleId());
        ruleMap.put("rule_name", rule.getRuleName());
        ruleMap.put("rule_type", rule.getRuleType());
        ruleMap.put("apply_to", rule.getApplyTo());
        ruleMap.put("target_field", rule.getTargetField());
        ruleMap.put("field_path", rule.getFieldPath());
        ruleMap.put("rule_expression", rule.getRuleExpression());
        ruleMap.put("error_message", rule.getErrorMessage());
        ruleMap.put("priority", rule.getPriority());
        ruleMap.put("active", rule.getIsActive());
        return ruleMap;
    }
    
    /**
     * 将 CompletionRule 转换为 Map 格式
     */
    private Map<String, Object> convertCompletionRuleToMap(RuleEngine.CompletionRule rule) {
        Map<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("id", rule.getId());
        ruleMap.put("rule_name", rule.getRuleName());
        ruleMap.put("rule_type", "completion");
        ruleMap.put("apply_to", rule.getApplyTo());
        ruleMap.put("target_field", rule.getTargetField());
        ruleMap.put("rule_expression", rule.getRuleExpression());
        ruleMap.put("priority", rule.getPriority());
        ruleMap.put("active", rule.isActive());
        return ruleMap;
    }
    
    /**
     * 将 ValidationRule 转换为 Map 格式
     */
    private Map<String, Object> convertValidationRuleToMap(RuleEngine.ValidationRule rule) {
        Map<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("id", rule.getId());
        ruleMap.put("rule_name", rule.getRuleName());
        ruleMap.put("rule_type", "validation");
        ruleMap.put("apply_to", rule.getApplyTo());
        ruleMap.put("field_path", rule.getFieldPath());
        ruleMap.put("rule_expression", rule.getRuleExpression());
        ruleMap.put("error_message", rule.getErrorMessage());
        ruleMap.put("priority", rule.getPriority());
        ruleMap.put("active", rule.isActive());
        return ruleMap;
    }
    
    /**
     * 获取补全规则
     * 
     * @return 补全规则列表
     */
    @GetMapping("/completion")
    public ResponseEntity<Map<String, Object>> getCompletionRules() {
        try {
            log.info("获取补全规则列表，当前引擎: {}", ruleEngineConfigService.getCurrentEngine());
            
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> completionRules = new ArrayList<>();
            Map<String, Object> ruleStats;
            
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 使用 SpEL 引擎
                spelRuleEngine.loadRules("../shared/config/rules_spel.yaml");
                ruleStats = spelRuleEngine.getRuleStats();
                
                List<BusinessRule> spelCompletionRules = spelRuleEngine.getCompletionRules();
                completionRules = spelCompletionRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
            } else {
                // 使用 CEL 引擎
                ruleEngine.loadRules("../shared/config/rules.yaml");
                ruleStats = ruleEngine.getRuleStats();
                
                List<RuleEngine.CompletionRule> celCompletionRules = ruleEngine.getCompletionRules();
                completionRules = celCompletionRules.stream()
                    .map(this::convertCompletionRuleToMap)
                    .collect(Collectors.toList());
            }
            
            response.put("rules", completionRules);
            response.put("message", "补全规则列表");
            response.put("totalRules", ruleStats.get("completion_rules_count"));
            response.put("activeRules", ruleStats.get("active_completion_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            response.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            log.info("补全规则列表获取完成 - 引擎: {}, 总数: {}, 活跃: {}", 
                ruleEngineConfigService.getCurrentEngine(),
                ruleStats.get("completion_rules_count"), ruleStats.get("active_completion_rules"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取补全规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取补全规则列表失败: " + e.getMessage());
            errorResponse.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取验证规则
     * 
     * @return 验证规则列表
     */
    @GetMapping("/validation")
    public ResponseEntity<Map<String, Object>> getValidationRules() {
        try {
            log.info("获取验证规则列表，当前引擎: {}", ruleEngineConfigService.getCurrentEngine());
            
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> validationRules = new ArrayList<>();
            Map<String, Object> ruleStats;
            
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 使用 SpEL 引擎
                spelRuleEngine.loadRules("../shared/config/rules_spel.yaml");
                ruleStats = spelRuleEngine.getRuleStats();
                
                List<BusinessRule> spelValidationRules = spelRuleEngine.getValidationRules();
                validationRules = spelValidationRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
            } else {
                // 使用 CEL 引擎
                ruleEngine.loadRules("../shared/config/rules.yaml");
                ruleStats = ruleEngine.getRuleStats();
                
                List<RuleEngine.ValidationRule> celValidationRules = ruleEngine.getValidationRules();
                validationRules = celValidationRules.stream()
                    .map(this::convertValidationRuleToMap)
                    .collect(Collectors.toList());
            }
            
            response.put("rules", validationRules);
            response.put("message", "验证规则列表");
            response.put("totalRules", ruleStats.get("validation_rules_count"));
            response.put("activeRules", ruleStats.get("active_validation_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            response.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            log.info("验证规则列表获取完成 - 引擎: {}, 总数: {}, 活跃: {}", 
                ruleEngineConfigService.getCurrentEngine(),
                ruleStats.get("validation_rules_count"), ruleStats.get("active_validation_rules"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取验证规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取验证规则列表失败: " + e.getMessage());
            errorResponse.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取域字段列表
     * 
     * @return 域字段列表
     */
    @GetMapping("/domain-fields")
    public ResponseEntity<Map<String, Object>> getDomainFields() {
        try {
            log.info("获取域字段列表");
            
            Map<String, Object> fields = new HashMap<>();
            
            // 发票基本字段
            List<Map<String, Object>> invoiceFields = new ArrayList<>();
            invoiceFields.add(Map.of("name", "invoiceNumber", "type", "String", "description", "发票号码"));
            invoiceFields.add(Map.of("name", "issueDate", "type", "LocalDate", "description", "开票日期"));
            invoiceFields.add(Map.of("name", "totalAmount", "type", "BigDecimal", "description", "总金额"));
            invoiceFields.add(Map.of("name", "taxAmount", "type", "BigDecimal", "description", "税额"));
            invoiceFields.add(Map.of("name", "currency", "type", "String", "description", "币种"));
            
            // 供应商字段
            List<Map<String, Object>> supplierFields = new ArrayList<>();
            supplierFields.add(Map.of("name", "supplier.name", "type", "String", "description", "供应商名称"));
            supplierFields.add(Map.of("name", "supplier.taxNo", "type", "String", "description", "供应商税号"));
            supplierFields.add(Map.of("name", "supplier.legalRepresentative", "type", "String", "description", "法定代表人"));
            
            // 客户字段
            List<Map<String, Object>> customerFields = new ArrayList<>();
            customerFields.add(Map.of("name", "customer.name", "type", "String", "description", "客户名称"));
            customerFields.add(Map.of("name", "customer.taxNo", "type", "String", "description", "客户税号"));
            customerFields.add(Map.of("name", "customer.legalRepresentative", "type", "String", "description", "法定代表人"));
            
            fields.put("invoice", invoiceFields);
            fields.put("supplier", supplierFields);
            fields.put("customer", customerFields);
            
            log.info("域字段列表获取完成");
            return ResponseEntity.ok(fields);
            
        } catch (Exception e) {
            log.error("获取域字段列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取域字段列表失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取可用函数列表
     * 
     * @return 可用函数列表
     */
    @GetMapping("/functions")
    public ResponseEntity<List<Map<String, Object>>> getAvailableFunctions() {
        try {
            log.info("获取可用函数列表");
            
            List<Map<String, Object>> functions = new ArrayList<>();
            
            // has() 函数
            functions.add(Map.of(
                "name", "has",
                "description", "检查字段是否存在且不为空",
                "syntax", "has(field_path)",
                "example", "has(invoice.customer.name)",
                "returnType", "boolean"
            ));
            
            // get_tax_rate() 函数
            functions.add(Map.of(
                "name", "get_tax_rate",
                "description", "根据企业类别获取推荐税率",
                "syntax", "get_tax_rate(category)",
                "example", "get_tax_rate('TECH')",
                "returnType", "BigDecimal"
            ));
            
            // now() 函数
            functions.add(Map.of(
                "name", "now",
                "description", "获取当前日期时间",
                "syntax", "now()",
                "example", "now()",
                "returnType", "LocalDateTime"
            ));
            
            log.info("可用函数列表获取完成，共 {} 个函数", functions.size());
            return ResponseEntity.ok(functions);
            
        } catch (Exception e) {
            log.error("获取可用函数列表失败", e);
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }
    
    /**
     * 重新加载规则
     * 
     * @return 重新加载结果
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadRules() {
        try {
            log.info("重新加载规则配置");
            
            // 重新加载规则
            ruleEngine.loadRules("../shared/config/rules.yaml");
            
            // 获取重新加载后的统计信息
            Map<String, Object> ruleStats = ruleEngine.getRuleStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "规则重新加载成功");
            response.put("totalCompletionRules", ruleStats.get("completion_rules_count"));
            response.put("totalValidationRules", ruleStats.get("validation_rules_count"));
            response.put("activeCompletionRules", ruleStats.get("active_completion_rules"));
            response.put("activeValidationRules", ruleStats.get("active_validation_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            
            log.info("规则重新加载完成 - 完成规则: {}, 验证规则: {}", 
                ruleStats.get("completion_rules_count"), ruleStats.get("validation_rules_count"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("重新加载规则失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "重新加载规则失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}