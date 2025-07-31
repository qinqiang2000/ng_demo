package com.invoice.spel;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.InvoiceItem;
import com.invoice.models.BusinessRule;
import com.invoice.spel.services.DbService;
import com.invoice.spel.services.ItemService;
import com.invoice.spel.utils.SpelHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SpEL 规则引擎
 * 
 * 提供基于 Spring Expression Language (SpEL) 的规则处理功能：
 * - 字段补全规则执行
 * - 数据验证规则执行
 * - 批量商品处理
 * - 混合规则引擎支持
 */
@Component
@Slf4j
public class SpelRuleEngine {
    
    @Autowired
    private SpelExpressionEvaluator spelEvaluator;
    
    @Autowired
    private DbService dbService;
    
    @Autowired
    private ItemService itemService;
    
    @Autowired
    private SpelHelper spelHelper;
    
    @Autowired
    private SpelFieldSetter spelFieldSetter;
    
    // SpEL 表达式解析器
    private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
    
    // 规则缓存
    private List<BusinessRule> completionRules = new ArrayList<>();
    private List<BusinessRule> validationRules = new ArrayList<>();
    private boolean rulesLoaded = false;
    
    // 执行日志
    private List<Map<String, Object>> completionExecutionLog = new ArrayList<>();
    private List<Map<String, Object>> validationExecutionLog = new ArrayList<>();
    
    /**
     * 判断目标字段是否为集合类型（需要逐个处理商品）
     * 
     * 特殊处理：对于批量处理规则（如 completion_item_001），虽然 target_field 包含投影操作符，
     * 但 rule_expression 调用的是批量处理方法，应该使用发票级别处理而不是商品级别处理。
     * 
     * @param targetField 目标字段表达式
     * @param invoice 发票对象
     * @return 是否为集合类型
     */
    private boolean isCollectionTargetField(String targetField, InvoiceDomainObject invoice) {
        try {
            // 检查是否为 SpEL 投影表达式
            if (targetField.contains(".![")) {
                log.debug("字段 {} 包含投影操作符 '.![', 判断为集合类型", targetField);
                return true;
            }
            
            // 解析 SpEL 表达式来获取集合本身的类型
            Expression expr = spelExpressionParser.parseExpression(targetField);
            
            // 直接在发票对象上评估表达式
            Object value = expr.getValue(invoice);
            
            if (value != null) {
                // 判断是否为集合类型或数组类型
                boolean isCollection = value instanceof Collection || value.getClass().isArray();
                log.debug("字段 {} 类型检查: {} -> 是否为集合: {}", targetField, value.getClass().getSimpleName(), isCollection);
                return isCollection;
            }
            
            return false;
        } catch (Exception e) {
            log.warn("无法通过反射判断字段 {} 的类型，回退到字符串匹配: {}", targetField, e.getMessage());
            // 回退到原有的字符串匹配逻辑
            return targetField != null && (targetField.contains("items") || targetField.contains("[]") || targetField.contains(".!["));
        }
    }
    
    /**
      * 判断规则是否应该使用批量处理模式
      * 
      * @param rule 业务规则
      * @return 是否使用批量处理
      */
     private boolean shouldUseBatchProcessing(BusinessRule rule) {
         // 检查 rule_expression 是否调用批量处理方法
         String expression = rule.getRuleExpression();
         if (expression != null) {
             // 检查是否调用了批量处理方法
             if (expression.contains("completeAllItemNames") || 
                 expression.contains("completeAllItems") ||
                 expression.contains("batchProcess")) {
                 log.debug("规则 {} 使用批量处理方法，应在发票级别处理", rule.getRuleId());
                 return true;
             }
         }
         return false;
     }
     
     /**
      * 从字段路径中提取字段名
      * 
      * @param fieldPath 字段路径表达式
      * @return 字段名
      */
     private String extractFieldNameFromPath(String fieldPath) {
         if (fieldPath == null) {
             return null;
         }
         
         if (fieldPath.trim().isEmpty()) {
             return "";
         }
         
         // 处理投影语法 items.![name] -> name
         if (fieldPath.contains(".![") && fieldPath.endsWith("]")) {
             int start = fieldPath.lastIndexOf(".![") + 3;
             int end = fieldPath.lastIndexOf("]");
             if (start < end) {
                 return fieldPath.substring(start, end);
             }
         }
         
         // 处理普通路径 invoice.supplier.name -> name
         if (fieldPath.contains(".")) {
             return fieldPath.substring(fieldPath.lastIndexOf(".") + 1);
         }
         
         // 简单字段名直接返回
         return fieldPath;
     }
    
    /**
     * 加载SpEL规则配置
     */
    public void loadRules(String configPath) {
        try {
            log.info("开始加载SpEL规则配置: {}", configPath);
            
            Yaml yaml = new Yaml();
            InputStream inputStream = new FileInputStream(configPath);
            Map<String, Object> config = yaml.load(inputStream);
            
            log.info("SpEL YAML配置加载成功，配置键: {}", config.keySet());
            
            // 加载字段补全规则
            List<Map<String, Object>> completionRulesConfig = 
                (List<Map<String, Object>>) config.get("field_completion_rules");
            
            log.info("找到SpEL补全规则配置: {}", completionRulesConfig != null ? completionRulesConfig.size() : "null");
            
            if (completionRulesConfig != null) {
                 this.completionRules = completionRulesConfig.stream()
                     .map(ruleConfig -> parseBusinessRule(ruleConfig, "completion"))
                     .filter(Objects::nonNull)
                     .filter(rule -> rule.getIsActive())
                     .sorted(Comparator.comparingInt(BusinessRule::getPriority).reversed())
                     .collect(Collectors.toList());
                     
                 log.info("解析SpEL补全规则数量: {}", this.completionRules.size());
                 for (BusinessRule rule : this.completionRules) {
                     log.info("SpEL补全规则: {} - {} (激活: {})", rule.getRuleId(), rule.getRuleName(), rule.getIsActive());
                 }
             }
             
             // 加载字段验证规则
             List<Map<String, Object>> validationRulesConfig = 
                 (List<Map<String, Object>>) config.get("field_validation_rules");
             
             log.info("找到SpEL验证规则配置: {}", validationRulesConfig != null ? validationRulesConfig.size() : "null");
             
             if (validationRulesConfig != null) {
                 this.validationRules = validationRulesConfig.stream()
                     .map(ruleConfig -> parseBusinessRule(ruleConfig, "validation"))
                     .filter(Objects::nonNull)
                     .filter(rule -> rule.getIsActive())
                     .sorted(Comparator.comparingInt(BusinessRule::getPriority).reversed())
                     .collect(Collectors.toList());
                     
                 log.info("解析SpEL验证规则数量: {}", this.validationRules.size());
                 for (BusinessRule rule : this.validationRules) {
                     log.info("SpEL验证规则: {} - {} (激活: {})", rule.getRuleId(), rule.getRuleName(), rule.getIsActive());
                 }
             }
            
            this.rulesLoaded = true;
            
            log.info("SpEL规则加载完成 - 补全规则: {}, 验证规则: {}", 
                completionRules.size(), validationRules.size());
            
        } catch (Exception e) {
            log.error("SpEL规则加载失败: {}", configPath, e);
            throw new RuntimeException("SpEL规则加载失败", e);
        }
    }
    
    /**
      * 解析业务规则配置
      */
     private BusinessRule parseBusinessRule(Map<String, Object> ruleConfig, String ruleType) {
         try {
             BusinessRule rule = new BusinessRule();
             rule.setRuleId((String) ruleConfig.get("id"));
             rule.setRuleName((String) ruleConfig.get("rule_name"));
             rule.setIsActive((Boolean) ruleConfig.getOrDefault("active", true));
             rule.setPriority((Integer) ruleConfig.getOrDefault("priority", 0));
             rule.setApplyTo((String) ruleConfig.get("apply_to"));
             rule.setTargetField((String) ruleConfig.get("target_field"));
             rule.setRuleExpression((String) ruleConfig.get("rule_expression"));
             rule.setErrorMessage((String) ruleConfig.get("error_message"));
             
             // 直接设置传入的规则类型
             rule.setRuleType(ruleType);
             
             return rule;
         } catch (Exception e) {
             log.error("解析业务规则配置失败: {}", ruleConfig, e);
             return null;
         }
     }

    /**
     * 应用字段补全规则
     * 
     * @param invoice 发票对象
     * @param rules 规则列表
     * @return 执行日志
     */
    public Map<String, Object> applyCompletionRules(InvoiceDomainObject invoice, List<BusinessRule> rules) {
        // 如果传入的规则列表为空，使用内部加载的规则
        List<BusinessRule> rulesToApply = (rules == null || rules.isEmpty()) ? this.completionRules : rules;
        
        // 如果内部规则也为空，尝试加载规则
        if (rulesToApply.isEmpty() && !rulesLoaded) {
            loadRules("../shared/config/rules_spel.yaml");
            rulesToApply = this.completionRules;
        }
        
        Map<String, Object> executionLog = new HashMap<>();
        List<Map<String, Object>> ruleResults = new ArrayList<>();
        
        log.info("开始应用 SpEL 字段补全规则，规则数量: {}", rulesToApply.size());
        
        for (BusinessRule rule : rulesToApply) {
            if (!"completion".equals(rule.getRuleType())) {
                continue;
            }
            
            System.out.println("\n" + "=".repeat(60));
            log.info("🔧 开始处理补全规则 - ID: {}, 名称: {}", rule.getRuleId(), rule.getRuleName());
            
            Map<String, Object> ruleResult = new HashMap<>();
            ruleResult.put("rule_id", rule.getRuleId());
            ruleResult.put("rule_name", rule.getRuleName());
            ruleResult.put("rule_type", rule.getRuleType());
            
            // 创建补全日志条目
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("rule_id", rule.getRuleId());
            logEntry.put("rule_name", rule.getRuleName());
            logEntry.put("rule_type", "completion");
            logEntry.put("target_field", rule.getTargetField());
            logEntry.put("expression", rule.getRuleExpression());
            logEntry.put("timestamp", System.currentTimeMillis());
            
            try {
                // 检查规则适用条件
                if (!isRuleApplicable(invoice, rule)) {
                    log.info("⏭️  规则 {} (ID: {}) 不适用，跳过处理", rule.getRuleName(), rule.getRuleId());
                    ruleResult.put("status", "skipped");
                    ruleResult.put("message", "规则不适用");
                    
                    logEntry.put("status", "skipped");
                    logEntry.put("message", "规则不适用");
                    
                    ruleResults.add(ruleResult);
                    completionExecutionLog.add(logEntry);
                    continue;
                }
                
                log.info("✅ 规则 {} (ID: {}) 适用条件检查通过，开始执行", rule.getRuleName(), rule.getRuleId());
                
                // 检查是否为批量处理规则
                if (shouldUseBatchProcessing(rule)) {
                    log.info("📦 执行批量处理补全规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    applyInvoiceCompletionRule(invoice, rule, ruleResult);
                    
                    logEntry.put("status", ruleResult.get("status"));
                    logEntry.put("message", ruleResult.get("message"));
                    logEntry.put("field", ruleResult.get("field"));
                    logEntry.put("value", ruleResult.get("value"));
                    
                } else if (isCollectionTargetField(rule.getTargetField(), invoice)) {
                    log.info("📋 执行商品级别补全规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    applyItemCompletionRule(invoice, rule, ruleResult);
                    
                    logEntry.put("status", ruleResult.get("status"));
                    logEntry.put("message", ruleResult.get("message"));
                    logEntry.put("item_results", ruleResult.get("item_results"));
                    
                } else {
                    // 处理发票级别规则
                    log.info("📄 执行发票级别补全规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    applyInvoiceCompletionRule(invoice, rule, ruleResult);
                    
                    logEntry.put("status", ruleResult.get("status"));
                    logEntry.put("message", ruleResult.get("message"));
                    logEntry.put("field", ruleResult.get("field"));
                    logEntry.put("value", ruleResult.get("value"));
                }
                
                log.info("✅ 补全规则 {} (ID: {}) 执行完成", rule.getRuleName(), rule.getRuleId());
                
            } catch (Exception e) {
                log.error("❌ 应用补全规则 {} (ID: {}) 时发生异常: {}", rule.getRuleName(), rule.getRuleId(), e.getMessage(), e);
                ruleResult.put("status", "error");
                ruleResult.put("message", "规则执行异常: " + e.getMessage());
                
                logEntry.put("status", "error");
                logEntry.put("message", "规则执行异常: " + e.getMessage());
            }
            
            ruleResults.add(ruleResult);
            completionExecutionLog.add(logEntry);
        }
        
        executionLog.put("rule_results", ruleResults);
        executionLog.put("total_rules", rulesToApply.size());
        executionLog.put("completion_rules", ruleResults.size());
        
        log.info("SpEL 字段补全规则应用完成，处理规则数: {}", ruleResults.size());
        return executionLog;
    }
    
    /**
     * 应用数据验证规则
     * 
     * @param invoice 发票对象
     * @param rules 规则列表
     * @return 验证结果
     */
    public Map<String, Object> applyValidationRules(InvoiceDomainObject invoice, List<BusinessRule> rules) {
        // 如果传入的规则列表为空，使用内部加载的规则
        List<BusinessRule> rulesToApply = (rules == null || rules.isEmpty()) ? this.validationRules : rules;
        
        // 如果内部规则也为空，尝试加载规则
        if (rulesToApply.isEmpty() && !rulesLoaded) {
            loadRules("../shared/config/rules_spel.yaml");
            rulesToApply = this.validationRules;
        }

        Map<String, Object> validationResult = new HashMap<>();
        List<Map<String, Object>> ruleResults = new ArrayList<>();
        boolean allValid = true;
        
        log.info("开始应用 SpEL 数据验证规则，规则数量: {}", rulesToApply.size());
        
        for (BusinessRule rule : rulesToApply) {
            if (!"validation".equals(rule.getRuleType())) {
                continue;
            }
            
            System.out.println("\n" + "=".repeat(60));
            log.info("🔍 开始处理验证规则 - ID: {}, 名称: {}", rule.getRuleId(), rule.getRuleName());
            
            Map<String, Object> ruleResult = new HashMap<>();
            ruleResult.put("rule_id", rule.getRuleId());
            ruleResult.put("rule_name", rule.getRuleName());
            ruleResult.put("rule_type", rule.getRuleType());
            
            // 创建验证日志条目
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("rule_id", rule.getRuleId());
            logEntry.put("rule_name", rule.getRuleName());
            logEntry.put("rule_type", "validation");
            logEntry.put("target_field", rule.getTargetField());
            logEntry.put("expression", rule.getRuleExpression());
            logEntry.put("timestamp", System.currentTimeMillis());
            
            try {
                // 检查规则适用条件
                if (!isRuleApplicable(invoice, rule)) {
                    log.info("⏭️  验证规则 {} (ID: {}) 不适用，跳过处理", rule.getRuleName(), rule.getRuleId());
                    ruleResult.put("status", "skipped");
                    ruleResult.put("message", "规则不适用");
                    
                    logEntry.put("status", "skipped");
                    logEntry.put("message", "规则不适用");
                    logEntry.put("valid", true);
                    
                    ruleResults.add(ruleResult);
                    validationExecutionLog.add(logEntry);
                    continue;
                }
                
                log.info("✅ 验证规则 {} (ID: {}) 适用条件检查通过，开始执行", rule.getRuleName(), rule.getRuleId());
                
                // 通过反射判断是否为集合类型规则
                if (isCollectionTargetField(rule.getTargetField(), invoice)) {
                    log.info("📋 执行商品级别验证规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    boolean itemValid = applyItemValidationRule(invoice, rule, ruleResult);
                    if (!itemValid) {
                        allValid = false;
                    }
                    
                    logEntry.put("status", "success");
                    logEntry.put("valid", itemValid);
                    logEntry.put("message", itemValid ? "商品级别验证通过" : "商品级别验证失败");
                    logEntry.put("item_results", ruleResult.get("item_results"));
                    
                } else {
                    // 处理发票级别规则
                    log.info("📄 执行发票级别验证规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    boolean invoiceValid = applyInvoiceValidationRule(invoice, rule, ruleResult);
                    if (!invoiceValid) {
                        allValid = false;
                    }
                    
                    logEntry.put("status", "success");
                    logEntry.put("valid", invoiceValid);
                    logEntry.put("message", invoiceValid ? "发票级别验证通过" : "发票级别验证失败");
                }
                
                log.info("✅ 验证规则 {} (ID: {}) 执行完成", rule.getRuleName(), rule.getRuleId());
                
            } catch (Exception e) {
                log.error("❌ 应用验证规则 {} (ID: {}) 时发生异常: {}", rule.getRuleName(), rule.getRuleId(), e.getMessage(), e);
                ruleResult.put("status", "error");
                ruleResult.put("message", "规则执行异常: " + e.getMessage());
                
                logEntry.put("status", "error");
                logEntry.put("valid", false);
                logEntry.put("message", "规则执行异常: " + e.getMessage());
                
                allValid = false;
            }
            
            ruleResults.add(ruleResult);
            validationExecutionLog.add(logEntry);
        }
        
        validationResult.put("all_valid", allValid);
        validationResult.put("rule_results", ruleResults);
        validationResult.put("total_rules", rulesToApply.size());
        validationResult.put("validation_rules", ruleResults.size());
        
        log.info("SpEL 数据验证规则应用完成，处理规则数: {}，验证结果: {}", ruleResults.size(), allValid ? "通过" : "失败");
        return validationResult;
    }
    
    /**
     * 检查规则是否适用
     * 
     * @param invoice 发票对象
     * @param rule 规则
     * @return 是否适用
     */
    private boolean isRuleApplicable(InvoiceDomainObject invoice, BusinessRule rule) {
        if (rule.getApplyTo() == null || rule.getApplyTo().trim().isEmpty()) {
            return true;
        }
        
        try {
            // 构建 SpEL 上下文
            Map<String, Object> services = Map.of(
                "dbService", dbService,
                "itemService", itemService,
                "helper", spelHelper
            );
            
            Map<String, Object> context = spelHelper.buildSpelContext(invoice, null, services);
            
            // 评估条件表达式
            Object result = spelEvaluator.evaluate(rule.getApplyTo(), invoice, null);
            return Boolean.TRUE.equals(result);
            
        } catch (Exception e) {
            log.warn("检查规则 {} 适用条件时发生异常: {}", rule.getRuleName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 应用发票级别补全规则
     * 
     * @param invoice 发票对象
     * @param rule 规则
     * @param ruleResult 规则结果
     */
    private void applyInvoiceCompletionRule(InvoiceDomainObject invoice, BusinessRule rule, Map<String, Object> ruleResult) {
        try {
            // 构建 SpEL 上下文
            Map<String, Object> services = Map.of(
                "dbService", dbService,
                "itemService", itemService,
                "helper", spelHelper
            );
            
            Map<String, Object> context = spelHelper.buildSpelContext(invoice, null, services);
            
            log.info("开始评估表达式: {}", rule.getRuleExpression());
            // 评估表达式并设置字段值
            Object value = spelEvaluator.evaluate(rule.getRuleExpression(), invoice, null);
            log.info("表达式评估完成，结果: {}", value);
            
            setExtensionField(invoice, rule.getTargetField(), value);
            
            ruleResult.put("status", "success");
            ruleResult.put("field", rule.getTargetField());
            ruleResult.put("value", value);
            ruleResult.put("message", "字段补全成功");
            
            log.debug("发票字段 {} 补全成功: {}", rule.getTargetField(), value);
            
        } catch (Exception e) {
            log.error("应用发票补全规则 {} 时发生异常: {}", rule.getRuleName(), e.getMessage());
            ruleResult.put("status", "error");
            ruleResult.put("message", "字段补全失败: " + e.getMessage());
        }
    }
    
    /**
     * 应用商品级别补全规则
     * 
     * @param invoice 发票对象
     * @param rule 规则
     * @param ruleResult 规则结果
     */
    private void applyItemCompletionRule(InvoiceDomainObject invoice, BusinessRule rule, Map<String, Object> ruleResult) {
        List<Map<String, Object>> itemResults = new ArrayList<>();
        int successCount = 0;
        int skippedCount = 0;
        
        for (int i = 0; i < invoice.getItems().size(); i++) {
            InvoiceItem item = invoice.getItems().get(i);
            Map<String, Object> itemResult = new HashMap<>();
            itemResult.put("item_index", i);
            itemResult.put("item_name", item.getName());
            
            try {
                // 首先检查 apply_to 条件是否满足
                boolean shouldApply = true;
                if (rule.getApplyTo() != null && !rule.getApplyTo().trim().isEmpty()) {
                    Object conditionResult = spelEvaluator.evaluate(rule.getApplyTo(), invoice, item);
                    shouldApply = Boolean.TRUE.equals(conditionResult);
                    log.debug("商品 {} apply_to 条件 '{}' 评估结果: {}", item.getName(), rule.getApplyTo(), shouldApply);
                }
                
                if (!shouldApply) {
                    itemResult.put("status", "skipped");
                    itemResult.put("message", "不满足 apply_to 条件，跳过处理");
                    skippedCount++;
                    log.debug("商品 {} 不满足 apply_to 条件，跳过补全", item.getName());
                } else {
                    // 构建包含当前商品的 SpEL 上下文
                    Map<String, Object> services = Map.of(
                        "dbService", dbService,
                        "itemService", itemService,
                        "helper", spelHelper
                    );
                    
                    Map<String, Object> context = spelHelper.buildSpelContext(invoice, item, services);
                    
                    // 评估表达式并设置字段值
                    Object value = spelEvaluator.evaluate(rule.getRuleExpression(), invoice, item);
                    
                    // 使用 SpelFieldSetter 设置商品字段
                    boolean success = spelFieldSetter.setFieldValue(item, rule.getTargetField(), value);
                    
                    if (success) {
                        itemResult.put("status", "success");
                        itemResult.put("field", rule.getTargetField());
                        itemResult.put("value", value);
                        itemResult.put("message", "字段补全成功");
                        successCount++;
                        log.debug("商品 {} 字段 {} 补全成功: {}", item.getName(), rule.getTargetField(), value);
                    } else {
                        itemResult.put("status", "error");
                        itemResult.put("field", rule.getTargetField());
                        itemResult.put("message", "字段设置失败");
                        log.warn("商品 {} 字段 {} 设置失败", item.getName(), rule.getTargetField());
                    }
                }
                
            } catch (Exception e) {
                log.error("应用商品补全规则 {} 到商品 {} 时发生异常: {}", 
                         rule.getRuleName(), item.getName(), e.getMessage());
                itemResult.put("status", "error");
                itemResult.put("message", "字段补全失败: " + e.getMessage());
            }
            
            itemResults.add(itemResult);
        }
        
        ruleResult.put("status", successCount > 0 ? "success" : "error");
        ruleResult.put("item_results", itemResults);
        ruleResult.put("success_count", successCount);
        ruleResult.put("skipped_count", skippedCount);
        ruleResult.put("total_items", invoice.getItems().size());
        ruleResult.put("message", String.format("成功处理 %d/%d 个商品，跳过 %d 个", successCount, invoice.getItems().size(), skippedCount));
    }
    
    /**
     * 应用发票级别验证规则
     * 
     * @param invoice 发票对象
     * @param rule 规则
     * @param ruleResult 规则结果
     * @return 验证是否通过
     */
    private boolean applyInvoiceValidationRule(InvoiceDomainObject invoice, BusinessRule rule, Map<String, Object> ruleResult) {
        try {
            // 构建 SpEL 上下文
            Map<String, Object> services = Map.of(
                "dbService", dbService,
                "itemService", itemService,
                "helper", spelHelper
            );
            
            Map<String, Object> context = spelHelper.buildSpelContext(invoice, null, services);
            
            // 评估验证表达式
            Object result = spelEvaluator.evaluate(rule.getRuleExpression(), invoice, null);
            boolean isValid = Boolean.TRUE.equals(result);
            
            ruleResult.put("status", "success");
            ruleResult.put("valid", isValid);
            ruleResult.put("message", isValid ? "验证通过" : "验证失败");
            
            log.debug("发票验证规则 {} 结果: {}", rule.getRuleName(), isValid ? "通过" : "失败");
            return isValid;
            
        } catch (Exception e) {
            log.error("应用发票验证规则 {} 时发生异常: {}", rule.getRuleName(), e.getMessage());
            ruleResult.put("status", "error");
            ruleResult.put("valid", false);
            ruleResult.put("message", "验证异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 应用商品级别验证规则
     * 
     * @param invoice 发票对象
     * @param rule 规则
     * @param ruleResult 规则结果
     * @return 验证是否通过
     */
    private boolean applyItemValidationRule(InvoiceDomainObject invoice, BusinessRule rule, Map<String, Object> ruleResult) {
        List<Map<String, Object>> itemResults = new ArrayList<>();
        int validCount = 0;
        int skippedCount = 0;
        
        for (int i = 0; i < invoice.getItems().size(); i++) {
            InvoiceItem item = invoice.getItems().get(i);
            Map<String, Object> itemResult = new HashMap<>();
            itemResult.put("item_index", i);
            itemResult.put("item_name", item.getName());
            
            try {
                // 首先检查 apply_to 条件是否满足
                boolean shouldApply = true;
                if (rule.getApplyTo() != null && !rule.getApplyTo().trim().isEmpty()) {
                    Object conditionResult = spelEvaluator.evaluate(rule.getApplyTo(), invoice, item);
                    shouldApply = Boolean.TRUE.equals(conditionResult);
                    log.debug("商品 {} apply_to 条件 '{}' 评估结果: {}", item.getName(), rule.getApplyTo(), shouldApply);
                }
                
                if (!shouldApply) {
                    itemResult.put("status", "skipped");
                    itemResult.put("valid", true); // 跳过的商品视为验证通过
                    itemResult.put("message", "不满足 apply_to 条件，跳过验证");
                    skippedCount++;
                    validCount++; // 跳过的商品也计入有效数量
                    log.debug("商品 {} 不满足 apply_to 条件，跳过验证", item.getName());
                } else {
                    // 构建包含当前商品的 SpEL 上下文
                    Map<String, Object> services = Map.of(
                        "dbService", dbService,
                        "itemService", itemService,
                        "helper", spelHelper
                    );
                    
                    Map<String, Object> context = spelHelper.buildSpelContext(invoice, item, services);
                    
                    // 评估验证表达式
                    Object result = spelEvaluator.evaluate(rule.getRuleExpression(), invoice, item);
                    boolean isValid = Boolean.TRUE.equals(result);
                    
                    itemResult.put("status", "success");
                    itemResult.put("valid", isValid);
                    itemResult.put("message", isValid ? "验证通过" : "验证失败");
                    
                    if (isValid) {
                        validCount++;
                    }
                    
                    log.debug("商品 {} 验证规则 {} 结果: {}", item.getName(), rule.getRuleName(), 
                             isValid ? "通过" : "失败");
                }
                
            } catch (Exception e) {
                log.error("应用商品验证规则 {} 到商品 {} 时发生异常: {}", 
                         rule.getRuleName(), item.getName(), e.getMessage());
                itemResult.put("status", "error");
                itemResult.put("valid", false);
                itemResult.put("message", "验证异常: " + e.getMessage());
            }
            
            itemResults.add(itemResult);
        }
        
        boolean allValid = validCount == invoice.getItems().size();
        
        ruleResult.put("status", "success");
        ruleResult.put("valid", allValid);
        ruleResult.put("item_results", itemResults);
        ruleResult.put("valid_count", validCount);
        ruleResult.put("skipped_count", skippedCount);
        ruleResult.put("total_items", invoice.getItems().size());
        ruleResult.put("message", String.format("验证通过 %d/%d 个商品，跳过 %d 个", validCount, invoice.getItems().size(), skippedCount));
        
        return allValid;
    }
    
    /**
     * 设置扩展字段值
     * 
     * @param invoice 发票对象
     * @param fieldName 字段名
     * @param value 字段值
     */
    private void setExtensionField(InvoiceDomainObject invoice, String fieldName, Object value) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return;
        }
        
        try {
            Object convertedValue = convertValue(value);
            
            // 解析字段路径并设置到对应的对象属性
            if (fieldName.startsWith("invoice.")) {
                setInvoiceField(invoice, fieldName, convertedValue);
            } else {
                // 对于非invoice开头的字段，设置到扩展字段中
                if (invoice.getExtensions() == null) {
                    invoice.setExtensions(new HashMap<>());
                }
                invoice.getExtensions().put(fieldName, convertedValue);
                log.debug("设置发票扩展字段: {} = {}", fieldName, convertedValue);
            }
            
        } catch (Exception e) {
            log.error("设置发票字段 {} 失败: {}", fieldName, e.getMessage());
        }
    }
    
    /**
     * 值类型转换
     * 
     * @param value 原始值
     * @return 转换后的值
     */
    private Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 字符串类型处理
        if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.trim().isEmpty()) {
                return null;
            }
            return strValue.trim();
        }
        
        // 数值类型直接返回
        if (value instanceof Number) {
            return value;
        }
        
        // 布尔类型直接返回
        if (value instanceof Boolean) {
            return value;
        }
        
        // List 类型直接返回（用于批量处理）
        if (value instanceof List) {
            return value;
        }
        
        // 其他类型转换为字符串
        return value.toString();
    }
    
    /**
     * 设置发票对象字段值
     * 使用纯 SpEL 方式直接设置字段，完全消除硬编码
     * 
     * @param invoice 发票对象
     * @param fieldPath 字段路径 (如: invoice.supplier.taxNo, invoice.items.![unitPrice])
     * @param value 字段值
     */
    private void setInvoiceField(InvoiceDomainObject invoice, String fieldPath, Object value) {
        try {
            log.info("使用纯 SpEL 设置发票字段: fieldPath='{}', value='{}'", fieldPath, value);
            
            // 去掉 invoice. 前缀，因为我们已经将 invoice 对象作为目标对象
            String actualFieldPath = fieldPath;
            if (fieldPath.startsWith("invoice.")) {
                actualFieldPath = fieldPath.substring("invoice.".length());
                log.debug("去掉 invoice. 前缀: {} -> {}", fieldPath, actualFieldPath);
            }
            
            // 使用 SpelFieldSetter 直接设置字段
            boolean success = spelFieldSetter.setFieldValue(invoice, actualFieldPath, value);
            
            if (success) {
                log.info("SpEL 字段设置成功: {} = {}", actualFieldPath, value);
            } else {
                log.warn("SpEL 字段设置失败: {}", actualFieldPath);
            }
            
        } catch (Exception e) {
            log.error("设置发票字段路径 {} 失败: {}", fieldPath, e.getMessage());
        }
    }
    
    /**
     * 获取字段补全执行日志
     */
    public List<Map<String, Object>> getCompletionExecutionLog() {
        return new ArrayList<>(completionExecutionLog);
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
     * 获取规则统计信息
     */
    public Map<String, Object> getRuleStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("rules_loaded", rulesLoaded);
        stats.put("completion_rules_count", completionRules.size());
        stats.put("validation_rules_count", validationRules.size());
        stats.put("active_completion_rules", completionRules.stream()
            .mapToInt(rule -> rule.getIsActive() ? 1 : 0).sum());
        stats.put("active_validation_rules", validationRules.stream()
            .mapToInt(rule -> rule.getIsActive() ? 1 : 0).sum());
        
        return stats;
    }
    
    /**
     * 获取字段补全规则列表
     */
    public List<BusinessRule> getCompletionRules() {
        return new ArrayList<>(completionRules);
    }
    
    /**
     * 获取字段验证规则列表
     */
    public List<BusinessRule> getValidationRules() {
        return new ArrayList<>(validationRules);
    }
}