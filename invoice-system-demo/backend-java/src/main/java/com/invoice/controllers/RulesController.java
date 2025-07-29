package com.invoice.controllers;

import com.invoice.core.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 获取所有规则
     * 
     * @return 规则列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRules() {
        try {
            log.info("获取规则列表");
            
            // 获取规则引擎统计信息
            Map<String, Object> ruleStats = ruleEngine.getRuleStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("completionRules", new ArrayList<>());
            response.put("validationRules", new ArrayList<>());
            response.put("message", "规则统计信息");
            response.put("totalCompletionRules", ruleStats.get("completion_rules_count"));
            response.put("totalValidationRules", ruleStats.get("validation_rules_count"));
            response.put("activeCompletionRules", ruleStats.get("active_completion_rules"));
            response.put("activeValidationRules", ruleStats.get("active_validation_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            
            log.info("规则列表获取完成 - 完成规则: {}, 验证规则: {}", 
                ruleStats.get("completion_rules_count"), ruleStats.get("validation_rules_count"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取规则列表失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取补全规则
     * 
     * @return 补全规则列表
     */
    @GetMapping("/completion")
    public ResponseEntity<Map<String, Object>> getCompletionRules() {
        try {
            log.info("获取补全规则列表");
            
            // 获取规则引擎统计信息
            Map<String, Object> ruleStats = ruleEngine.getRuleStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("rules", new ArrayList<>());
            response.put("message", "补全规则统计信息");
            response.put("totalRules", ruleStats.get("completion_rules_count"));
            response.put("activeRules", ruleStats.get("active_completion_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            
            log.info("补全规则列表获取完成 - 总数: {}, 活跃: {}", 
                ruleStats.get("completion_rules_count"), ruleStats.get("active_completion_rules"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取补全规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取补全规则列表失败: " + e.getMessage());
            
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
            log.info("获取验证规则列表");
            
            // 获取规则引擎统计信息
            Map<String, Object> ruleStats = ruleEngine.getRuleStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("rules", new ArrayList<>());
            response.put("message", "验证规则统计信息");
            response.put("totalRules", ruleStats.get("validation_rules_count"));
            response.put("activeRules", ruleStats.get("active_validation_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            
            log.info("验证规则列表获取完成 - 总数: {}, 活跃: {}", 
                ruleStats.get("validation_rules_count"), ruleStats.get("active_validation_rules"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取验证规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取验证规则列表失败: " + e.getMessage());
            
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