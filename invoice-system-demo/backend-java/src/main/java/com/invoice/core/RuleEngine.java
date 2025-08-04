package com.invoice.core;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import com.invoice.spel.SpelFieldSetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.util.*;
import java.util.Objects;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CEL规则引擎
 * 
 * 基于Google CEL-Java标准库的规则引擎实现
 * 支持字段补全和业务验证规则
 * 使用CEL表达式进行动态计算和条件判断
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEngine {

    private final CelExpressionEvaluator expressionEvaluator;
    private final SpelFieldSetter fieldSetter; // 通用字段设置器，虽然底层使用SpEL技术，但在CEL引擎中作为字段设置工具

    private List<CompletionRule> completionRules = new ArrayList<>();
    private List<ValidationRule> validationRules = new ArrayList<>();
    private boolean rulesLoaded = false;

    // 执行日志
    private List<Map<String, Object>> completionExecutionLog = new ArrayList<>();
    private List<Map<String, Object>> validationExecutionLog = new ArrayList<>();
    
    // Context缓存 - 避免重复转换Invoice为Map
    private Map<String, Object> cachedContext = null;
    private InvoiceDomainObject cachedInvoice = null;
    
    // Item上下文缓存 - 避免为相同item重复创建上下文
    private Map<String, Object> cachedItemContext = null;
    private InvoiceDomainObject cachedItemContextInvoice = null;
    private com.invoice.domain.InvoiceItem cachedItemContextItem = null;

    /**
     * 获取字段补全执行日志
     */
    public List<Map<String, Object>> getCompletionExecutionLog() {
        return new ArrayList<>(completionExecutionLog);
    }
    
    /**
     * 优化的Context创建方法 - 使用缓存避免重复转换
     */
    private Map<String, Object> getOrCreateContext(InvoiceDomainObject invoice) {
        // 检查是否可以使用缓存
        if (cachedContext != null && cachedInvoice != null && isSameInvoice(cachedInvoice, invoice)) {
            log.debug("使用缓存的Context，避免重复转换Invoice");
            return cachedContext;
        }
        
        // 创建新的Context并缓存
        log.debug("创建新的Context并缓存");
        cachedContext = expressionEvaluator.createContext(invoice);
        cachedInvoice = copyInvoice(invoice); // 保存副本用于比较
        return cachedContext;
    }
    
    /**
     * 检查两个Invoice是否相同（用于缓存判断）
     * 使用hashCode进行快速比较，避免深度对象比较的性能开销
     */
    private boolean isSameInvoice(InvoiceDomainObject invoice1, InvoiceDomainObject invoice2) {
        if (invoice1 == invoice2) return true;
        if (invoice1 == null || invoice2 == null) return false;
        
        // 使用hashCode进行快速比较
        // 注意：这种方法假设Invoice对象正确实现了hashCode方法
        return invoice1.hashCode() == invoice2.hashCode() &&
               Objects.equals(invoice1.getInvoiceNumber(), invoice2.getInvoiceNumber());
    }
    
    /**
     * 清除Context缓存
     */
    private void clearContextCache() {
        cachedContext = null;
        cachedInvoice = null;
        cachedItemContext = null;
        cachedItemContextInvoice = null;
        cachedItemContextItem = null;
        log.debug("已清除Context缓存");
        // 同时清除CelExpressionEvaluator的缓存
        expressionEvaluator.clearInvoiceCache();
    }
    
    /**
     * 获取或创建item上下文，使用缓存避免重复创建
     */
    private Map<String, Object> getOrCreateItemContext(InvoiceDomainObject invoice, com.invoice.domain.InvoiceItem item) {
        // 检查缓存是否有效
        if (cachedItemContext != null && 
            isSameInvoice(cachedItemContextInvoice, invoice) &&
            isSameItem(cachedItemContextItem, item)) {
            log.debug("使用缓存的item上下文，避免重复转换");
            return cachedItemContext;
        }
        
        // 缓存无效，重新创建并缓存
        log.debug("创建新的item上下文并缓存");
        Map<String, Object> baseContext = getOrCreateContext(invoice);
        Map<String, Object> itemContext = expressionEvaluator.createContextWithItem(baseContext, item);
        
        // 缓存item上下文
        cachedItemContext = itemContext;
        cachedItemContextInvoice = copyInvoice(invoice);
        cachedItemContextItem = copyItem(item);
        
        return itemContext;
    }
    
    /**
     * 检查两个Item对象是否相同
     */
    private boolean isSameItem(com.invoice.domain.InvoiceItem item1, com.invoice.domain.InvoiceItem item2) {
        if (item1 == null || item2 == null) {
            return item1 == item2;
        }
        
        // 比较hashCode和关键字段
        return item1.hashCode() == item2.hashCode() &&
               Objects.equals(item1.getName(), item2.getName()) &&
               Objects.equals(item1.getDescription(), item2.getDescription()) &&
               Objects.equals(item1.getQuantity(), item2.getQuantity()) &&
               Objects.equals(item1.getUnitPrice(), item2.getUnitPrice());
    }
    
    /**
     * 复制Item对象
     */
    private com.invoice.domain.InvoiceItem copyItem(com.invoice.domain.InvoiceItem original) {
        if (original == null) return null;
        
        com.invoice.domain.InvoiceItem copy = new com.invoice.domain.InvoiceItem();
        copy.setName(original.getName());
        copy.setDescription(original.getDescription());
        copy.setQuantity(original.getQuantity());
        copy.setUnitPrice(original.getUnitPrice());
        copy.setLineTotal(original.getLineTotal());
        copy.setTaxRate(original.getTaxRate());
        copy.setTaxAmount(original.getTaxAmount());
        copy.setTaxCategory(original.getTaxCategory());
        copy.setAmount(original.getAmount());
        copy.setUnit(original.getUnit());
        copy.setProductCode(original.getProductCode());
        copy.setCategory(original.getCategory());
        return copy;
    }

    /**
     * 公共方法：清除所有缓存
     * 建议在返回报文给客户端之前调用，以释放内存
     */
    public void clearAllCaches() {
        log.debug("清除所有缓存（包括Context和Expression缓存）");
        clearContextCache();
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
                // 使用缓存的item上下文方法
                Map<String, Object> itemContext = getOrCreateItemContext(invoice, item);
                
                // 检查规则是否适用于当前item
                if (rule.getApplyTo() != null && !rule.getApplyTo().trim().isEmpty()) {
                    log.info("检查规则 {} 对item[{}]的适用条件: {}", rule.getId(), i, rule.getApplyTo());
                    Object applyResult = expressionEvaluator.evaluate(rule.getApplyTo(), itemContext);
                    log.info("规则 {} 对item[{}]的适用条件结果: {}", rule.getId(), i, applyResult);
                    if (!isTrue(applyResult)) {
                        log.info("规则 {} 对item[{}]适用条件不满足，跳过", rule.getId(), i);
                        continue;
                    }
                }

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
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getApplyTo() {
            return applyTo;
        }

        public void setApplyTo(String applyTo) {
            this.applyTo = applyTo;
        }

        public String getTargetField() {
            return targetField;
        }

        public void setTargetField(String targetField) {
            this.targetField = targetField;
        }

        public String getRuleExpression() {
            return ruleExpression;
        }

        public void setRuleExpression(String ruleExpression) {
            this.ruleExpression = ruleExpression;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
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
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getApplyTo() {
            return applyTo;
        }

        public void setApplyTo(String applyTo) {
            this.applyTo = applyTo;
        }

        public String getFieldPath() {
            return fieldPath;
        }

        public void setFieldPath(String fieldPath) {
            this.fieldPath = fieldPath;
        }

        public String getRuleExpression() {
            return ruleExpression;
        }

        public void setRuleExpression(String ruleExpression) {
            this.ruleExpression = ruleExpression;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
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
        public boolean isValid() {
            return isValid;
        }

        public void setValid(boolean valid) {
            isValid = valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
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
            List<Map<String, Object>> completionRulesConfig = (List<Map<String, Object>>) config
                    .get("field_completion_rules");

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
            List<Map<String, Object>> validationRulesConfig = (List<Map<String, Object>>) config
                    .get("field_validation_rules");

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
        // 清空之前的执行日志
        completionExecutionLog.clear();
        
        // 移除强制缓存清除，让getOrCreateContext智能判断是否需要重新创建

        if (!rulesLoaded) {
            log.info("规则未加载，开始加载规则...");
            loadRules("../shared/config/rules.yaml");
        }

        log.info("开始字段补全，发票号: {}，共有 {} 条补全规则", invoice.getInvoiceNumber(), completionRules.size());

        // 使用缓存的Context创建方法，避免重复转换
        InvoiceDomainObject workingInvoice = copyInvoice(invoice);
        Map<String, Object> context = getOrCreateContext(workingInvoice);
        int fieldsCompleted = 0;

        for (CompletionRule rule : completionRules) {
            System.out.println("\n" + "=".repeat(60));
            log.info("🔧 开始处理补全规则 - ID: {}, 名称: {}", rule.getId(), rule.getRuleName());

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
                        logEntry.put("message",
                                String.format("规则跳过: %s - 条件不满足: %s", rule.getRuleName(), rule.getApplyTo()));
                        completionExecutionLog.add(logEntry);

                        continue;
                    }
                }

                // 计算字段值
                log.info("执行规则 {} 表达式: {}", rule.getId(), rule.getRuleExpression());
                Object fieldValue = expressionEvaluator.evaluate(rule.getRuleExpression(), context);
                log.info("规则 {} 表达式结果: {}", rule.getId(), fieldValue);

                // 设置字段值
                boolean fieldUpdated = false;
                if (rule.getTargetField().startsWith("items[].")) {
                    // 对于items[]字段，需要为每个item单独处理
                    if (processItemsArrayRule(workingInvoice, rule)) {
                        fieldsCompleted++;
                        fieldUpdated = true;
                        log.info("items[]字段补全成功: {}", rule.getTargetField());
                    } else {
                        log.warn("items[]字段补全失败: {}", rule.getTargetField());
                    }
                } else {
                    // 对于非items[]字段，使用原有逻辑
                    if (setFieldValue(workingInvoice, rule.getTargetField(), fieldValue)) {
                        fieldsCompleted++;
                        fieldUpdated = true;
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
                                rule.getRuleName(), rule.getTargetField(),
                                rule.getTargetField().replace("invoice.", ""), fieldValue));
                        completionExecutionLog.add(logEntry);
                    } else {
                        log.warn("字段补全失败: {} = {} (设置失败)", rule.getTargetField(), fieldValue);

                        // 记录失败的日志
                        Map<String, Object> logEntry = new HashMap<>();
                        logEntry.put("type", "completion");
                        logEntry.put("status", "failed");
                        logEntry.put("rule_name", rule.getRuleName());
                        logEntry.put("target_field", rule.getTargetField());
                        logEntry.put("value", convertToSerializableValue(fieldValue));
                        logEntry.put("message", String.format("字段补全失败: %s - %s = %s (设置失败)",
                                rule.getRuleName(), rule.getTargetField(), fieldValue));
                        completionExecutionLog.add(logEntry);
                    }
                }

                // 移除contextNeedsUpdate标记，改为在所有规则执行完毕后统一处理缓存

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

        // 移除条件判断，不再需要contextNeedsUpdate变量

        // 调试：检查补全后的税号状态
        log.info("补全后供应商税号: {}",
                workingInvoice.getSupplier() != null ? workingInvoice.getSupplier().getTaxNo() : "null");
        log.info("补全后客户税号: {}",
                workingInvoice.getCustomer() != null ? workingInvoice.getCustomer().getTaxNo() : "null");

        log.info("字段补全完成，发票号: {}，完成字段数: {}，执行日志数: {}",
                workingInvoice.getInvoiceNumber(), fieldsCompleted, completionExecutionLog.size());

        // 保留缓存，让getOrCreateContext智能判断是否需要重新创建
        // 这样可以避免频繁的Invoice到Map转换

        return workingInvoice;
    }

    /**
     * 业务验证
     */
    public ValidationResult validateInvoice(InvoiceDomainObject invoice) {
        // 清空之前的验证执行日志
        validationExecutionLog.clear();
        
        if (!rulesLoaded) {
            loadRules("../shared/config/ru[les.yaml");
        }

        // 移除强制缓存清除，让getOrCreateContext智能判断是否需要重新创建
        
        log.debug("开始业务验证，发票号: {}", invoice.getInvoiceNumber());

        ValidationResult result = new ValidationResult();
        // 使用缓存的Context创建方法，避免重复转换
        Map<String, Object> context = getOrCreateContext(invoice);

        for (ValidationRule rule : validationRules) {
            System.out.println("\n" + "=".repeat(60));
            log.info("🔧 开始处理验证规则 - ID: {}, 名称: {}", rule.getId(), rule.getRuleName());
            
            if (!rule.isActive()) {
                log.info("规则 {} 未激活，跳过", rule.getId());
                
                // 记录跳过的日志
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("type", "validation");
                logEntry.put("status", "skipped");
                logEntry.put("rule_id", rule.getId());
                logEntry.put("rule_name", rule.getRuleName());
                logEntry.put("reason", "rule_inactive");
                logEntry.put("message", String.format("规则跳过: %s - 规则未激活", rule.getRuleName()));
                validationExecutionLog.add(logEntry);
                
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
                        logEntry.put("type", "validation");
                        logEntry.put("status", "skipped");
                        logEntry.put("rule_id", rule.getId());
                        logEntry.put("rule_name", rule.getRuleName());
                        logEntry.put("reason", "condition_not_met");
                        logEntry.put("condition", rule.getApplyTo());
                        logEntry.put("message", String.format("规则跳过: %s - 条件不满足: %s", rule.getRuleName(), rule.getApplyTo()));
                        validationExecutionLog.add(logEntry);
                        
                        continue;
                    }
                }

                // 执行验证规则
                log.info("执行规则 {} 表达式: {}", rule.getId(), rule.getRuleExpression());
                Object validationResult = expressionEvaluator.evaluate(rule.getRuleExpression(), context);
                log.info("规则 {} 表达式结果: {}", rule.getId(), validationResult);

                if (!isTrue(validationResult)) {
                    String errorMessage = rule.getErrorMessage() != null ? rule.getErrorMessage()
                            : "验证失败: " + rule.getRuleName();
                    result.getErrors().add(errorMessage);

                    log.debug("验证失败: {} - {}", rule.getId(), errorMessage);
                    
                    // 记录验证失败的日志
                    Map<String, Object> logEntry = new HashMap<>();
                    logEntry.put("type", "validation");
                    logEntry.put("status", "failed");
                    logEntry.put("rule_id", rule.getId());
                    logEntry.put("rule_name", rule.getRuleName());
                    logEntry.put("expression", rule.getRuleExpression());
                    logEntry.put("result", convertToSerializableValue(validationResult));
                    logEntry.put("error_message", errorMessage);
                    logEntry.put("message", String.format("验证失败: %s - %s", rule.getRuleName(), errorMessage));
                    validationExecutionLog.add(logEntry);
                } else {
                    log.info("验证通过: {} - {}", rule.getId(), rule.getRuleName());
                    
                    // 记录验证通过的日志
                    Map<String, Object> logEntry = new HashMap<>();
                    logEntry.put("type", "validation");
                    logEntry.put("status", "passed");
                    logEntry.put("rule_id", rule.getId());
                    logEntry.put("rule_name", rule.getRuleName());
                    logEntry.put("expression", rule.getRuleExpression());
                    logEntry.put("result", convertToSerializableValue(validationResult));
                    logEntry.put("message", String.format("验证通过: %s", rule.getRuleName()));
                    validationExecutionLog.add(logEntry);
                }

            } catch (Exception e) {
                log.warn("验证规则执行失败: {} - {}", rule.getId(), e.getMessage());
                result.getWarnings().add("规则执行异常: " + rule.getRuleName());
                
                // 记录执行异常的日志
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("type", "validation");
                logEntry.put("status", "error");
                logEntry.put("rule_id", rule.getId());
                logEntry.put("rule_name", rule.getRuleName());
                logEntry.put("expression", rule.getRuleExpression());
                logEntry.put("error", e.getMessage());
                logEntry.put("message", String.format("CEL验证规则执行异常: %s - %s", rule.getRuleName(), e.getMessage()));
                validationExecutionLog.add(logEntry);
            }
        }

        result.setValid(result.getErrors().isEmpty());
        result.setSummary(result.isValid() ? "所有验证规则通过" : String.format("发现 %d 个错误", result.getErrors().size()));

        log.info("业务验证完成，发票号: {}，结果: {}，执行日志数: {}",
                invoice.getInvoiceNumber(), result.getSummary(), validationExecutionLog.size());

        // 保留缓存，让getOrCreateContext智能判断是否需要重新创建
        
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
     * 使用通用的字段设置器提供基于反射的字段设置功能，消除硬编码
     * 注意：items[] 字段由主逻辑中的 processItemsArrayRule 方法专门处理，此方法不处理 items[] 字段
     * 
     * 本方法在CEL规则引擎中被调用，用于设置CEL表达式计算出的字段值
     */
    private boolean setFieldValue(InvoiceDomainObject invoice, String fieldPath, Object value) {
        log.info("setFieldValue调用: fieldPath='{}', value='{}'", fieldPath, value);

        if (value == null) {
            return false;
        }

        try {
            // items[] 字段由主逻辑中的 processItemsArrayRule 方法专门处理
            // 此方法不处理 items[] 字段，避免重复逻辑
            if (fieldPath.startsWith("items[].")) {
                log.warn("items[] 字段应由 processItemsArrayRule 方法处理，不应调用此方法: {}", fieldPath);
                return false;
            }
            
            // 转换字段路径：去掉 'invoice.' 前缀，因为字段设置器期望相对路径
            String relativePath = fieldPath;
            if (fieldPath.startsWith("invoice.")) {
                relativePath = fieldPath.substring(8); // 去掉 "invoice." 前缀
                log.debug("转换字段路径: {} -> {}", fieldPath, relativePath);
            }
            
            // 使用字段设置器处理所有非 items[] 字段路径
            // 字段设置器支持:
            // - 普通字段: taxAmount, currency 等
            // - 嵌套对象字段: supplier.name, customer.address 等
            // - Map字段: extensions.supplier_category 等
            // - 投影表达式: items.![unitPrice] 等
            boolean result = fieldSetter.setFieldValue(invoice, relativePath, value);
            
            if (result) {
                log.debug("成功设置字段: {} = {}", fieldPath, value);
            } else {
                log.warn("设置字段失败: {} = {}", fieldPath, value);
            }
            
            return result;
            
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
                            .street(addressStr) // 将完整地址作为街道地址
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
                            .street(addressStr) // 将完整地址作为街道地址
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
                // .paymentDate(original.getPaymentDate()) // Field doesn't exist in
                // InvoiceDomainObject
                .supplier(original.getSupplier())
                .customer(original.getCustomer())
                .items(original.getItems())
                .notes(original.getNotes())
                .status(original.getStatus())
                // .tags(original.getTags()) // Field doesn't exist in InvoiceDomainObject
                // .metadata(original.getMetadata()) // Field doesn't exist in
                // InvoiceDomainObject
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