package com.invoice.core;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则引擎
 * 
 * Java 版本的 Python 规则引擎
 * 支持字段补全和业务验证规则
 * 使用Google CEL-Java标准库进行表达式求值
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEngine {

    private final CelExpressionEvaluator expressionEvaluator;
    
    private List<CompletionRule> completionRules = new ArrayList<>();
    private List<ValidationRule> validationRules = new ArrayList<>();
    private boolean rulesLoaded = false;
    
    // 执行日志
    private List<Map<String, Object>> completionExecutionLog = new ArrayList<>();
    private List<Map<String, Object>> validationExecutionLog = new ArrayList<>();
    
    /**
     * 获取字段补全执行日志
     */
    public List<Map<String, Object>> getCompletionExecutionLog() {
        return new ArrayList<>(completionExecutionLog);
    }
    
    /**
     * 设置扩展字段
     */
    private boolean setExtensionField(InvoiceDomainObject invoice, String fieldName, Object value) {
        log.info("setExtensionField调用: fieldName='{}', value='{}'", fieldName, value);
        
        // 确保extensions对象存在
        if (invoice.getExtensions() == null) {
            invoice.setExtensions(new java.util.HashMap<>());
        }
        
        switch (fieldName) {
            case "supplier_category":
                invoice.getExtensions().put("supplier_category", String.valueOf(value));
                log.info("设置扩展字段supplier_category: {}", value);
                return true;
            case "invoice_type":
                invoice.getExtensions().put("invoice_type", String.valueOf(value));
                return true;
            case "total_quantity":
                invoice.getExtensions().put("total_quantity", String.valueOf(value));
                return true;
            default:
                // 对于未知的扩展字段，直接设置到extensions map中
                invoice.getExtensions().put(fieldName, String.valueOf(value));
                log.info("设置扩展字段 {}: {}", fieldName, value);
                return true;
        }
    }
    
    /**
     * 设置items数组字段
     */
    /**
     * 处理items[]数组规则，为每个item创建包含当前item的上下文
     */
    private boolean processItemsArrayRule(InvoiceDomainObject invoice, CompletionRule rule) {
        log.info("processItemsArrayRule调用: rule='{}', targetField='{}'", rule.getRuleName(), rule.getTargetField());
        
        // 提取字段名（去掉items[].前缀）
        String itemField = rule.getTargetField().replace("items[].", "");
        log.info("提取的item字段名: {}", itemField);
        
        // 检查是否有items
        if (invoice.getItems() == null || invoice.getItems().isEmpty()) {
            log.warn("发票没有items或items为空");
            return false;
        }
        
        // 为每个item设置字段值
        boolean anySuccess = false;
        for (int i = 0; i < invoice.getItems().size(); i++) {
            com.invoice.domain.InvoiceItem item = invoice.getItems().get(i);
            log.info("处理第 {} 个item", i + 1);
            
            try {
                // 检查规则是否适用于当前item
                if (rule.getApplyTo() != null && !rule.getApplyTo().trim().isEmpty()) {
                    // 为当前item创建包含item上下文的CEL上下文
                    Map<String, Object> itemContext = expressionEvaluator.createContext(invoice, item, null);
                    log.info("检查规则 {} 对item[{}]的适用条件: {}", rule.getId(), i, rule.getApplyTo());
                    Object applyResult = expressionEvaluator.evaluate(rule.getApplyTo(), itemContext);
                    log.info("规则 {} 对item[{}]的适用条件结果: {}", rule.getId(), i, applyResult);
                    if (!isTrue(applyResult)) {
                        log.info("规则 {} 对item[{}]适用条件不满足，跳过", rule.getId(), i);
                        continue;
                    }
                }
                
                // 为当前item创建包含item上下文的CEL上下文
                Map<String, Object> itemContext = expressionEvaluator.createContext(invoice, item, null);
                
                // 计算字段值
                log.info("执行规则 {} 对item[{}]的表达式: {}", rule.getId(), i, rule.getRuleExpression());
                Object fieldValue = expressionEvaluator.evaluate(rule.getRuleExpression(), itemContext);
                log.info("规则 {} 对item[{}]的表达式结果: {}", rule.getId(), i, fieldValue);
                
                // 设置字段值
                boolean success = setItemField(item, itemField, fieldValue);
                if (success) {
                    anySuccess = true;
                    log.info("成功设置items[{}].{} = {}", i, itemField, fieldValue);
                    
                    // 记录每个item的成功日志，使用具体的数组索引
                    Map<String, Object> logEntry = new HashMap<>();
                    logEntry.put("type", "completion");
                    logEntry.put("status", "success");
                    logEntry.put("rule_name", rule.getRuleName());
                    logEntry.put("target_field", rule.getTargetField());
                    logEntry.put("actual_field_path", String.format("items[%d].%s", i, itemField));
                    logEntry.put("item_index", i);
                    logEntry.put("value", convertToSerializableValue(fieldValue));
                    logEntry.put("message", String.format("字段补全成功: %s - 设置 items[%d].%s = %s", 
                        rule.getRuleName(), i, itemField, fieldValue));
                    completionExecutionLog.add(logEntry);
                } else {
                    log.warn("设置items[{}].{}失败", i, itemField);
                    
                    // 记录每个item的失败日志
                    Map<String, Object> logEntry = new HashMap<>();
                    logEntry.put("type", "completion");
                    logEntry.put("status", "failed");
                    logEntry.put("rule_name", rule.getRuleName());
                    logEntry.put("target_field", rule.getTargetField());
                    logEntry.put("actual_field_path", String.format("items[%d].%s", i, itemField));
                    logEntry.put("item_index", i);
                    logEntry.put("message", String.format("字段补全失败: %s - 无法设置字段 items[%d].%s", 
                        rule.getRuleName(), i, itemField));
                    completionExecutionLog.add(logEntry);
                }
            } catch (Exception e) {
                log.warn("设置items[{}].{}时发生异常: {}", i, itemField, e.getMessage());
                
                // 记录每个item的异常日志
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("type", "completion");
                logEntry.put("status", "error");
                logEntry.put("rule_name", rule.getRuleName());
                logEntry.put("target_field", rule.getTargetField());
                logEntry.put("actual_field_path", String.format("items[%d].%s", i, itemField));
                logEntry.put("item_index", i);
                logEntry.put("error", e.getMessage());
                logEntry.put("message", String.format("字段补全错误: %s - items[%d].%s: %s", 
                    rule.getRuleName(), i, itemField, e.getMessage()));
                completionExecutionLog.add(logEntry);
            }
        }
        
        return anySuccess;
    }

    private boolean setItemsArrayField(InvoiceDomainObject invoice, String fieldPath, Object value, String ruleName) {
        log.info("setItemsArrayField调用: fieldPath='{}', value='{}'", fieldPath, value);
        
        // 提取字段名（去掉items[].前缀）
        String itemField = fieldPath.replace("items[].", "");
        log.info("提取的item字段名: {}", itemField);
        
        // 检查是否有items
        if (invoice.getItems() == null || invoice.getItems().isEmpty()) {
            log.warn("发票没有items或items为空");
            return false;
        }
        
        // 为每个item设置字段值
        boolean anySuccess = false;
        for (int i = 0; i < invoice.getItems().size(); i++) {
            com.invoice.domain.InvoiceItem item = invoice.getItems().get(i);
            log.info("处理第 {} 个item", i + 1);
            
            try {
                boolean success = setItemField(item, itemField, value);
                if (success) {
                    anySuccess = true;
                    log.info("成功设置items[{}].{} = {}", i, itemField, value);
                    
                    // 记录每个item的成功日志，使用具体的数组索引
                    Map<String, Object> logEntry = new HashMap<>();
                    logEntry.put("type", "completion");
                    logEntry.put("status", "success");
                    logEntry.put("rule_name", ruleName);
                    logEntry.put("target_field", fieldPath);
                    logEntry.put("actual_field_path", String.format("items[%d].%s", i, itemField));
                    logEntry.put("item_index", i);
                    logEntry.put("value", convertToSerializableValue(value));
                    logEntry.put("message", String.format("字段补全成功: %s - 设置 items[%d].%s = %s", 
                        ruleName, i, itemField, value));
                    completionExecutionLog.add(logEntry);
                } else {
                    log.warn("设置items[{}].{}失败", i, itemField);
                    
                    // 记录每个item的失败日志
                    Map<String, Object> logEntry = new HashMap<>();
                    logEntry.put("type", "completion");
                    logEntry.put("status", "failed");
                    logEntry.put("rule_name", ruleName);
                    logEntry.put("target_field", fieldPath);
                    logEntry.put("actual_field_path", String.format("items[%d].%s", i, itemField));
                    logEntry.put("item_index", i);
                    logEntry.put("message", String.format("字段补全失败: %s - 无法设置字段 items[%d].%s", 
                        ruleName, i, itemField));
                    completionExecutionLog.add(logEntry);
                }
            } catch (Exception e) {
                log.warn("设置items[{}].{}时发生异常: {}", i, itemField, e.getMessage());
                
                // 记录每个item的异常日志
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("type", "completion");
                logEntry.put("status", "error");
                logEntry.put("rule_name", ruleName);
                logEntry.put("target_field", fieldPath);
                logEntry.put("actual_field_path", String.format("items[%d].%s", i, itemField));
                logEntry.put("item_index", i);
                logEntry.put("error", e.getMessage());
                logEntry.put("message", String.format("字段补全错误: %s - items[%d].%s: %s", 
                    ruleName, i, itemField, e.getMessage()));
                completionExecutionLog.add(logEntry);
            }
        }
        
        return anySuccess;
    }
    
    /**
     * 设置单个item字段
     */
    private boolean setItemField(com.invoice.domain.InvoiceItem item, String fieldName, Object value) {
        log.info("setItemField调用: fieldName='{}', value='{}'", fieldName, value);
        
        // 转换CEL表达式结果为可序列化格式
        Object serializedValue = convertToSerializableValue(value);
        
        switch (fieldName) {
            case "name":
                item.setName(String.valueOf(serializedValue));
                return true;
            case "description":
                item.setDescription(String.valueOf(serializedValue));
                return true;
            case "tax_rate":
                if (serializedValue instanceof Number) {
                    item.setTaxRate(new BigDecimal(serializedValue.toString()));
                    return true;
                }
                return false;
            case "tax_category":
                item.setTaxCategory(String.valueOf(serializedValue));
                return true;
            case "quantity":
                if (serializedValue instanceof Number) {
                    item.setQuantity(new BigDecimal(serializedValue.toString()));
                    return true;
                }
                return false;
            case "unit_price":
                if (serializedValue instanceof Number) {
                    item.setUnitPrice(new BigDecimal(serializedValue.toString()));
                    return true;
                }
                return false;
            case "amount":
                if (serializedValue instanceof Number) {
                    item.setAmount(new BigDecimal(serializedValue.toString()));
                    return true;
                }
                return false;
            default:
                log.warn("不支持的item字段: {}", fieldName);
                return false;
        }
    }
    
    /**
     * 获取验证执行日志
     */
    public List<Map<String, Object>> getValidationExecutionLog() {
        return new ArrayList<>(validationExecutionLog);
    }
    
    /**
     * 清空执行日志
     */
    public void clearExecutionLogs() {
        completionExecutionLog.clear();
        validationExecutionLog.clear();
    }
    
    /**
     * 完成规则定义
     */
    public static class CompletionRule {
        private String id;
        private String ruleName;
        private String applyTo;
        private String targetField;
        private String ruleExpression;
        private int priority;
        private boolean active;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        
        public String getApplyTo() { return applyTo; }
        public void setApplyTo(String applyTo) { this.applyTo = applyTo; }
        
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        
        public String getRuleExpression() { return ruleExpression; }
        public void setRuleExpression(String ruleExpression) { this.ruleExpression = ruleExpression; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
    
    /**
     * 验证规则定义
     */
    public static class ValidationRule {
        private String id;
        private String ruleName;
        private String applyTo;
        private String fieldPath;
        private String ruleExpression;
        private String errorMessage;
        private int priority;
        private boolean active;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        
        public String getApplyTo() { return applyTo; }
        public void setApplyTo(String applyTo) { this.applyTo = applyTo; }
        
        public String getFieldPath() { return fieldPath; }
        public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
        
        public String getRuleExpression() { return ruleExpression; }
        public void setRuleExpression(String ruleExpression) { this.ruleExpression = ruleExpression; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private boolean isValid;
        private List<String> errors;
        private List<String> warnings;
        private String summary;
        
        public ValidationResult() {
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
        
        // Getters and setters
        public boolean isValid() { return isValid; }
        public void setValid(boolean valid) { isValid = valid; }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
    
    /**
     * 加载规则配置
     */
    public void loadRules(String configPath) {
        try {
            log.info("开始加载规则配置: {}", configPath);
            
            Yaml yaml = new Yaml();
            InputStream inputStream = new FileInputStream(configPath);
            Map<String, Object> config = yaml.load(inputStream);
            
            log.info("YAML配置加载成功，配置键: {}", config.keySet());
            
            // 加载完成规则 - 修正配置键名以匹配rules.yaml
            List<Map<String, Object>> completionRulesConfig = 
                (List<Map<String, Object>>) config.get("field_completion_rules");
            
            log.info("找到完成规则配置: {}", completionRulesConfig != null ? completionRulesConfig.size() : "null");
            
            if (completionRulesConfig != null) {
                this.completionRules = completionRulesConfig.stream()
                    .map(this::parseCompletionRule)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(CompletionRule::getPriority).reversed())
                    .collect(Collectors.toList());
                    
                log.info("解析完成规则数量: {}", this.completionRules.size());
                for (CompletionRule rule : this.completionRules) {
                    log.info("完成规则: {} - {} (激活: {})", rule.getId(), rule.getRuleName(), rule.isActive());
                }
            }
            
            // 加载验证规则 - 修正配置键名以匹配rules.yaml
            List<Map<String, Object>> validationRulesConfig = 
                (List<Map<String, Object>>) config.get("field_validation_rules");
            
            log.info("找到验证规则配置: {}", validationRulesConfig != null ? validationRulesConfig.size() : "null");
            
            if (validationRulesConfig != null) {
                this.validationRules = validationRulesConfig.stream()
                    .map(this::parseValidationRule)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(ValidationRule::getPriority).reversed())
                    .collect(Collectors.toList());
                    
                log.info("解析验证规则数量: {}", this.validationRules.size());
            }
            
            this.rulesLoaded = true;
            
            log.info("规则加载完成 - 完成规则: {}, 验证规则: {}", 
                completionRules.size(), validationRules.size());
            
        } catch (Exception e) {
            log.error("规则加载失败: {}", configPath, e);
            throw new RuntimeException("规则加载失败", e);
        }
    }
    
    /**
     * 字段补全
     */
    public InvoiceDomainObject completeFields(InvoiceDomainObject invoice) {
        log.info("=== RuleEngine.completeFields() 方法被调用 ===");
        log.info("传入发票对象: {}", invoice != null ? "非空" : "空");
        
        // 清空之前的执行日志
        completionExecutionLog.clear();
        
        if (!rulesLoaded) {
            log.info("规则未加载，开始加载规则...");
            loadRules("../shared/config/rules.yaml");
        }
        
        log.info("开始字段补全，发票号: {}，共有 {} 条补全规则", invoice.getInvoiceNumber(), completionRules.size());
        
        Map<String, Object> context = expressionEvaluator.createContext(invoice);
        InvoiceDomainObject workingInvoice = copyInvoice(invoice);
        int fieldsCompleted = 0;
        
        // 调试：检查供应商税号状态
        log.info("补全前供应商税号: {}", workingInvoice.getSupplier() != null ? workingInvoice.getSupplier().getTaxNo() : "null");
        log.info("补全前客户税号: {}", workingInvoice.getCustomer() != null ? workingInvoice.getCustomer().getTaxNo() : "null");
        
        for (CompletionRule rule : completionRules) {
            log.info("处理补全规则: {} ({}), 激活状态: {}", rule.getId(), rule.getRuleName(), rule.isActive());
            
            if (!rule.isActive()) {
                log.info("规则 {} 未激活，跳过", rule.getId());
                continue;
            }
            
            try {
                // 检查规则是否适用
                if (rule.getApplyTo() != null && !rule.getApplyTo().trim().isEmpty()) {
                    log.info("检查规则 {} 适用条件: {}", rule.getId(), rule.getApplyTo());
                    Object applyResult = expressionEvaluator.evaluate(rule.getApplyTo(), context);
                    log.info("规则 {} 适用条件结果: {}", rule.getId(), applyResult);
                    if (!isTrue(applyResult)) {
                        log.info("规则 {} 适用条件不满足，跳过", rule.getId());
                        
                        // 记录跳过的日志
                        Map<String, Object> logEntry = new HashMap<>();
                        logEntry.put("type", "completion");
                        logEntry.put("status", "skipped");
                        logEntry.put("rule_name", rule.getRuleName());
                        logEntry.put("reason", "condition_not_met");
                        logEntry.put("condition", rule.getApplyTo());
                        logEntry.put("message", String.format("规则跳过: %s - 条件不满足: %s", rule.getRuleName(), rule.getApplyTo()));
                        completionExecutionLog.add(logEntry);
                        
                        continue;
                    }
                }
                
                // 计算字段值
                log.info("执行规则 {} 表达式: {}", rule.getId(), rule.getRuleExpression());
                Object fieldValue = expressionEvaluator.evaluate(rule.getRuleExpression(), context);
                log.info("规则 {} 表达式结果: {}", rule.getId(), fieldValue);
                
                // 设置字段值
                if (rule.getTargetField().startsWith("items[].")) {
                    // 对于items[]字段，需要为每个item单独处理
                    if (processItemsArrayRule(workingInvoice, rule)) {
                        fieldsCompleted++;
                        log.info("items[]字段补全成功: {}", rule.getTargetField());
                        
                        // 更新上下文
                        context = expressionEvaluator.createContext(workingInvoice);
                    } else {
                        log.warn("items[]字段补全失败: {}", rule.getTargetField());
                    }
                } else {
                    // 对于非items[]字段，使用原有逻辑
                    if (setFieldValue(workingInvoice, rule.getTargetField(), fieldValue)) {
                        fieldsCompleted++;
                        log.info("字段补全成功: {} = {}", rule.getTargetField(), fieldValue);
                        
                        // 记录成功的日志
                        Map<String, Object> logEntry = new HashMap<>();
                        logEntry.put("type", "completion");
                        logEntry.put("status", "success");
                        logEntry.put("rule_name", rule.getRuleName());
                        logEntry.put("target_field", rule.getTargetField());
                        logEntry.put("actual_field_path", rule.getTargetField().replace("invoice.", ""));
                        logEntry.put("value", convertToSerializableValue(fieldValue));
                        logEntry.put("message", String.format("字段补全成功: %s - %s -> %s = %s", 
                            rule.getRuleName(), rule.getTargetField(), rule.getTargetField().replace("invoice.", ""), fieldValue));
                        completionExecutionLog.add(logEntry);
                        
                        // 更新上下文
                        context = expressionEvaluator.createContext(workingInvoice);
                    } else {
                        log.warn("字段补全失败: {} = {} (设置失败)", rule.getTargetField(), fieldValue);
                        
                        // 记录失败的日志
                        Map<String, Object> logEntry = new HashMap<>();
                        logEntry.put("type", "completion");
                        logEntry.put("status", "failed");
                        logEntry.put("rule_name", rule.getRuleName());
                        logEntry.put("target_field", rule.getTargetField());
                        logEntry.put("actual_field_path", rule.getTargetField().replace("invoice.", ""));
                        logEntry.put("message", String.format("字段补全失败: %s - 无法设置字段 %s -> %s", 
                            rule.getRuleName(), rule.getTargetField(), rule.getTargetField().replace("invoice.", "")));
                        completionExecutionLog.add(logEntry);
                    }
                }
                
            } catch (Exception e) {
                log.warn("字段补全规则执行失败: {} - {}", rule.getId(), e.getMessage(), e);
                
                // 记录错误的日志
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("type", "completion");
                logEntry.put("status", "error");
                logEntry.put("rule_name", rule.getRuleName());
                logEntry.put("error", e.getMessage());
                logEntry.put("message", String.format("CEL字段补全错误: %s - %s", rule.getRuleName(), e.getMessage()));
                completionExecutionLog.add(logEntry);
            }
        }
        
        // 调试：检查补全后的税号状态
        log.info("补全后供应商税号: {}", workingInvoice.getSupplier() != null ? workingInvoice.getSupplier().getTaxNo() : "null");
        log.info("补全后客户税号: {}", workingInvoice.getCustomer() != null ? workingInvoice.getCustomer().getTaxNo() : "null");
        
        log.info("字段补全完成，发票号: {}，完成字段数: {}，执行日志数: {}", 
            workingInvoice.getInvoiceNumber(), fieldsCompleted, completionExecutionLog.size());
        
        return workingInvoice;
    }
    
    /**
     * 业务验证
     */
    public ValidationResult validateInvoice(InvoiceDomainObject invoice) {
        if (!rulesLoaded) {
            loadRules("../shared/config/rules.yaml");
        }
        
        log.debug("开始业务验证，发票号: {}", invoice.getInvoiceNumber());
        
        ValidationResult result = new ValidationResult();
        Map<String, Object> context = expressionEvaluator.createContext(invoice);
        
        for (ValidationRule rule : validationRules) {
            if (!rule.isActive()) {
                continue;
            }
            
            try {
                // 检查规则是否适用
                if (rule.getApplyTo() != null && !rule.getApplyTo().trim().isEmpty()) {
                    Object applyResult = expressionEvaluator.evaluate(rule.getApplyTo(), context);
                    if (!isTrue(applyResult)) {
                        continue;
                    }
                }
                
                // 执行验证规则
                Object validationResult = expressionEvaluator.evaluate(rule.getRuleExpression(), context);
                
                if (!isTrue(validationResult)) {
                    String errorMessage = rule.getErrorMessage() != null ? 
                        rule.getErrorMessage() : "验证失败: " + rule.getRuleName();
                    result.getErrors().add(errorMessage);
                    
                    log.debug("验证失败: {} - {}", rule.getId(), errorMessage);
                }
                
            } catch (Exception e) {
                log.warn("验证规则执行失败: {} - {}", rule.getId(), e.getMessage());
                result.getWarnings().add("规则执行异常: " + rule.getRuleName());
            }
        }
        
        result.setValid(result.getErrors().isEmpty());
        result.setSummary(result.isValid() ? 
            "所有验证规则通过" : 
            String.format("发现 %d 个错误", result.getErrors().size()));
        
        log.info("业务验证完成，发票号: {}，结果: {}", 
            invoice.getInvoiceNumber(), result.getSummary());
        
        return result;
    }
    
    /**
     * 解析完成规则
     */
    private CompletionRule parseCompletionRule(Map<String, Object> ruleConfig) {
        try {
            CompletionRule rule = new CompletionRule();
            rule.setId((String) ruleConfig.get("id"));
            rule.setRuleName((String) ruleConfig.get("rule_name"));
            rule.setApplyTo((String) ruleConfig.get("apply_to"));
            rule.setTargetField((String) ruleConfig.get("target_field"));
            rule.setRuleExpression((String) ruleConfig.get("rule_expression"));
            rule.setPriority(((Number) ruleConfig.getOrDefault("priority", 50)).intValue());
            rule.setActive((Boolean) ruleConfig.getOrDefault("active", true));
            
            return rule;
        } catch (Exception e) {
            log.warn("解析完成规则失败: {}", ruleConfig, e);
            return null;
        }
    }
    
    /**
     * 解析验证规则
     */
    private ValidationRule parseValidationRule(Map<String, Object> ruleConfig) {
        try {
            ValidationRule rule = new ValidationRule();
            rule.setId((String) ruleConfig.get("id"));
            rule.setRuleName((String) ruleConfig.get("rule_name"));
            rule.setApplyTo((String) ruleConfig.get("apply_to"));
            rule.setFieldPath((String) ruleConfig.get("field_path"));
            rule.setRuleExpression((String) ruleConfig.get("rule_expression"));
            rule.setErrorMessage((String) ruleConfig.get("error_message"));
            rule.setPriority(((Number) ruleConfig.getOrDefault("priority", 50)).intValue());
            rule.setActive((Boolean) ruleConfig.getOrDefault("active", true));
            
            return rule;
        } catch (Exception e) {
            log.warn("解析验证规则失败: {}", ruleConfig, e);
            return null;
        }
    }
    
    /**
     * 设置字段值
     */
    private boolean setFieldValue(InvoiceDomainObject invoice, String fieldPath, Object value) {
        log.info("setFieldValue调用: fieldPath='{}', value='{}'", fieldPath, value);
        
        if (value == null) {
            return false;
        }
        
        try {
            String[] pathParts = fieldPath.split("\\.");
            log.info("字段路径解析: pathParts={}, length={}", Arrays.toString(pathParts), pathParts.length);
            
            // 处理发票字段
            if ("invoice".equals(pathParts[0]) && pathParts.length > 1) {
                String fieldName = pathParts[1];
                
                // 处理三级路径：invoice.supplier.xxx 和 invoice.customer.xxx
                if (pathParts.length == 3) {
                    if ("supplier".equals(fieldName)) {
                        log.info("调用setSupplierField: fieldName='{}', value='{}'", pathParts[2], value);
                        return setSupplierField(invoice, pathParts[2], value);
                    } else if ("customer".equals(fieldName)) {
                        log.info("调用setCustomerField: fieldName='{}', value='{}'", pathParts[2], value);
                        return setCustomerField(invoice, pathParts[2], value);
                    } else {
                        log.warn("不支持的发票嵌套字段路径: {}", fieldPath);
                        return false;
                    }
                }
                
                // 处理二级路径：invoice.xxx
                log.info("处理二级路径: fieldName='{}'", fieldName);
                switch (fieldName) {
                    case "tax_amount":
                        if (value instanceof Number) {
                            invoice.setTaxAmount(new BigDecimal(value.toString()));
                            return true;
                        }
                        return false;
                    case "currency":
                        invoice.setCurrency(String.valueOf(value));
                        return true;
                    case "notes":
                        invoice.setNotes(String.valueOf(value));
                        return true;
                    case "status":
                        invoice.setStatus(String.valueOf(value));
                        return true;
                    case "country":
                        invoice.setCountry(String.valueOf(value));
                        log.info("设置发票国家: {}", value);
                        return true;
                    case "extensions":
                        // 处理extensions字段，需要进一步解析路径
                        if (pathParts.length == 3) {
                            return setExtensionField(invoice, pathParts[2], value);
                        }
                        log.warn("extensions字段路径格式错误: {}", fieldPath);
                        return false;
                    default:
                        log.warn("不支持的发票字段路径: {}", fieldPath);
                        return false;
                }
            }
            // 处理items[]数组字段
            else if (fieldPath.startsWith("items[].")) {
                log.info("处理items[]数组字段: {}", fieldPath);
                return setItemsArrayField(invoice, fieldPath, value, "未知规则");
            }
            // 处理供应商字段
            else if ("supplier".equals(pathParts[0]) && pathParts.length > 1) {
                log.info("处理二级路径: entityType='supplier', fieldName='{}'", pathParts[1]);
                return setSupplierField(invoice, pathParts[1], value);
            }
            // 处理客户字段
            else if ("customer".equals(pathParts[0]) && pathParts.length > 1) {
                log.info("处理二级路径: entityType='customer', fieldName='{}'", pathParts[1]);
                return setCustomerField(invoice, pathParts[1], value);
            }
            else {
                log.warn("不支持的字段路径格式: {}", fieldPath);
                return false;
            }
            
        } catch (Exception e) {
            log.warn("设置字段值失败: {} = {}", fieldPath, value, e);
            return false;
        }
    }
    
    /**
     * 设置供应商字段
     */
    private boolean setSupplierField(InvoiceDomainObject invoice, String fieldName, Object value) {
        // 确保供应商对象存在
        if (invoice.getSupplier() == null) {
            invoice.setSupplier(Party.builder().build());
        }
        
        switch (fieldName) {
            case "tax_no":
            case "tax_id":
                invoice.getSupplier().setTaxNo(String.valueOf(value));
                log.info("设置供应商税号: {}", value);
                return true;
            case "name":
                invoice.getSupplier().setName(String.valueOf(value));
                return true;
            case "phone":
                invoice.getSupplier().setPhone(String.valueOf(value));
                return true;
            case "email":
                invoice.getSupplier().setEmail(String.valueOf(value));
                return true;
            case "company_type":
                invoice.getSupplier().setCompanyType(String.valueOf(value));
                return true;
            case "company_scale":
                invoice.getSupplier().setCompanyScale(String.valueOf(value));
                return true;
            case "industry_classification":
                invoice.getSupplier().setIndustryClassification(String.valueOf(value));
                return true;
            case "address":
                // 将字符串地址转换为Address对象
                if (value != null) {
                    String addressStr = String.valueOf(value);
                    com.invoice.domain.Address address = com.invoice.domain.Address.builder()
                        .street(addressStr)  // 将完整地址作为街道地址
                        .build();
                    invoice.getSupplier().setAddress(address);
                    log.info("设置供应商地址: {}", addressStr);
                    return true;
                }
                return false;
            default:
                log.warn("不支持的供应商字段: {}", fieldName);
                return false;
        }
    }
    
    /**
     * 设置客户字段
     */
    private boolean setCustomerField(InvoiceDomainObject invoice, String fieldName, Object value) {
        log.info("setCustomerField调用: fieldName='{}', value='{}'", fieldName, value);
        
        // 确保客户对象存在
        if (invoice.getCustomer() == null) {
            invoice.setCustomer(Party.builder().build());
        }
        
        switch (fieldName) {
            case "tax_no":
            case "tax_id":
                invoice.getCustomer().setTaxNo(String.valueOf(value));
                log.info("设置客户税号: {}", value);
                return true;
            case "name":
                invoice.getCustomer().setName(String.valueOf(value));
                return true;
            case "phone":
                invoice.getCustomer().setPhone(String.valueOf(value));
                return true;
            case "email":
                invoice.getCustomer().setEmail(String.valueOf(value));
                return true;
            case "company_type":
                invoice.getCustomer().setCompanyType(String.valueOf(value));
                return true;
            case "company_scale":
                invoice.getCustomer().setCompanyScale(String.valueOf(value));
                return true;
            case "industry_classification":
                invoice.getCustomer().setIndustryClassification(String.valueOf(value));
                return true;
            case "address":
                // 将字符串地址转换为Address对象
                if (value != null) {
                    String addressStr = String.valueOf(value);
                    com.invoice.domain.Address address = com.invoice.domain.Address.builder()
                        .street(addressStr)  // 将完整地址作为街道地址
                        .build();
                    invoice.getCustomer().setAddress(address);
                    log.info("设置客户地址: {}", addressStr);
                    return true;
                }
                return false;
            default:
                log.warn("不支持的客户字段: {}", fieldName);
                return false;
        }
    }
    
    /**
     * 复制发票对象
     */
    private InvoiceDomainObject copyInvoice(InvoiceDomainObject original) {
        // 简化实现，使用 builder 模式复制
        return InvoiceDomainObject.builder()
            .invoiceNumber(original.getInvoiceNumber())
            .totalAmount(original.getTotalAmount())
            .taxAmount(original.getTaxAmount())
            .netAmount(original.getNetAmount())
            .currency(original.getCurrency())
            .issueDate(original.getIssueDate())
            .dueDate(original.getDueDate())
            // .paymentDate(original.getPaymentDate()) // Field doesn't exist in InvoiceDomainObject
            .supplier(original.getSupplier())
            .customer(original.getCustomer())
            .items(original.getItems())
            .notes(original.getNotes())
            .status(original.getStatus())
            // .tags(original.getTags()) // Field doesn't exist in InvoiceDomainObject
            // .metadata(original.getMetadata()) // Field doesn't exist in InvoiceDomainObject
            .createdAt(original.getCreatedAt())
            .updatedAt(original.getUpdatedAt())
            .build();
    }
    
    /**
     * 判断值是否为真
     */
    private boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }
        return true;
    }
    
    /**
     * 获取规则统计信息
     */
    public Map<String, Object> getRuleStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rules_loaded", rulesLoaded);
        stats.put("completion_rules_count", completionRules.size());
        stats.put("validation_rules_count", validationRules.size());
        stats.put("active_completion_rules", completionRules.stream()
            .mapToInt(rule -> rule.isActive() ? 1 : 0).sum());
        stats.put("active_validation_rules", validationRules.stream()
            .mapToInt(rule -> rule.isActive() ? 1 : 0).sum());
        
        return stats;
    }
    
    /**
     * 获取字段补全规则列表
     */
    public List<CompletionRule> getCompletionRules() {
        return new ArrayList<>(completionRules);
    }
    
    /**
     * 获取字段验证规则列表
     */
    public List<ValidationRule> getValidationRules() {
        return new ArrayList<>(validationRules);
    }
    
    /**
     * 将CEL表达式返回值转换为可序列化的格式
     * 解决AutoValue_CelUnknownSet等CEL内部对象的序列化问题
     */
    private Object convertToSerializableValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 检查是否是CEL内部对象（通过类名和toString方法检测）
        String className = value.getClass().getName();
        String valueString = value.toString();
        
        // 检测CelUnknownSet或其他CEL内部对象
        if (className.contains("AutoValue_") || 
            className.contains("dev.cel.") || 
            className.contains("CelUnknownSet") ||
            valueString.contains("CelUnknownSet{") ||
            valueString.contains("unknownExprIds=")) {
            // 对于CEL内部对象，返回一个更友好的字符串表示
            return "待补全"; // 或者可以返回 valueString 如果需要保留原始信息
        }
        
        // 对于基本类型，直接返回
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        
        // 对于集合类型，递归处理
        if (value instanceof java.util.Collection) {
            java.util.List<Object> result = new java.util.ArrayList<>();
            for (Object item : (java.util.Collection<?>) value) {
                result.add(convertToSerializableValue(item));
            }
            return result;
        }
        
        // 对于Map类型，递归处理
        if (value instanceof java.util.Map) {
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), convertToSerializableValue(entry.getValue()));
            }
            return result;
        }
        
        // 对于其他对象，转换为字符串
        return value.toString();
    }
}