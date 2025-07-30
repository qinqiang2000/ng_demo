package com.invoice.spel;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.InvoiceItem;
import com.invoice.models.BusinessRule;
import com.invoice.spel.services.DbService;
import com.invoice.spel.services.ItemService;
import com.invoice.spel.utils.SpelHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    // 规则缓存
    private List<BusinessRule> completionRules = new ArrayList<>();
    private List<BusinessRule> validationRules = new ArrayList<>();
    private boolean rulesLoaded = false;
    
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
                     .map(this::parseBusinessRule)
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
                     .map(this::parseBusinessRule)
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
     private BusinessRule parseBusinessRule(Map<String, Object> ruleConfig) {
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
             
             // 根据配置来源设置规则类型
             if (ruleConfig.containsKey("target_field")) {
                 rule.setRuleType("completion");
             } else if (ruleConfig.containsKey("error_message")) {
                 rule.setRuleType("validation");
             }
             
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
            
            log.info("🔧 开始处理补全规则 - ID: {}, 名称: {}", rule.getRuleId(), rule.getRuleName());
            
            Map<String, Object> ruleResult = new HashMap<>();
            ruleResult.put("rule_id", rule.getRuleId());
            ruleResult.put("rule_name", rule.getRuleName());
            ruleResult.put("rule_type", rule.getRuleType());
            
            try {
                // 检查规则适用条件
                if (!isRuleApplicable(invoice, rule)) {
                    log.info("⏭️  规则 {} (ID: {}) 不适用，跳过处理", rule.getRuleName(), rule.getRuleId());
                    ruleResult.put("status", "skipped");
                    ruleResult.put("message", "规则不适用");
                    ruleResults.add(ruleResult);
                    continue;
                }
                
                log.info("✅ 规则 {} (ID: {}) 适用条件检查通过，开始执行", rule.getRuleName(), rule.getRuleId());
                
                // 处理数组项规则
                if (rule.getApplyTo() != null && rule.getApplyTo().contains("items[]")) {
                    log.info("📋 执行商品级别补全规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    applyItemCompletionRule(invoice, rule, ruleResult);
                } else {
                    // 处理发票级别规则
                    log.info("📄 执行发票级别补全规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    applyInvoiceCompletionRule(invoice, rule, ruleResult);
                }
                
                log.info("✅ 补全规则 {} (ID: {}) 执行完成", rule.getRuleName(), rule.getRuleId());
                
            } catch (Exception e) {
                log.error("❌ 应用补全规则 {} (ID: {}) 时发生异常: {}", rule.getRuleName(), rule.getRuleId(), e.getMessage(), e);
                ruleResult.put("status", "error");
                ruleResult.put("message", "规则执行异常: " + e.getMessage());
            }
            
            ruleResults.add(ruleResult);
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
            
            log.info("🔍 开始处理验证规则 - ID: {}, 名称: {}", rule.getRuleId(), rule.getRuleName());
            
            Map<String, Object> ruleResult = new HashMap<>();
            ruleResult.put("rule_id", rule.getRuleId());
            ruleResult.put("rule_name", rule.getRuleName());
            ruleResult.put("rule_type", rule.getRuleType());
            
            try {
                // 检查规则适用条件
                if (!isRuleApplicable(invoice, rule)) {
                    log.info("⏭️  验证规则 {} (ID: {}) 不适用，跳过处理", rule.getRuleName(), rule.getRuleId());
                    ruleResult.put("status", "skipped");
                    ruleResult.put("message", "规则不适用");
                    ruleResults.add(ruleResult);
                    continue;
                }
                
                log.info("✅ 验证规则 {} (ID: {}) 适用条件检查通过，开始执行", rule.getRuleName(), rule.getRuleId());
                
                // 处理数组项规则
                if (rule.getApplyTo() != null && rule.getApplyTo().contains("items[]")) {
                    log.info("📋 执行商品级别验证规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    boolean itemValid = applyItemValidationRule(invoice, rule, ruleResult);
                    if (!itemValid) {
                        allValid = false;
                    }
                } else {
                    // 处理发票级别规则
                    log.info("📄 执行发票级别验证规则: {} (ID: {})", rule.getRuleName(), rule.getRuleId());
                    boolean invoiceValid = applyInvoiceValidationRule(invoice, rule, ruleResult);
                    if (!invoiceValid) {
                        allValid = false;
                    }
                }
                
                log.info("✅ 验证规则 {} (ID: {}) 执行完成", rule.getRuleName(), rule.getRuleId());
                
            } catch (Exception e) {
                log.error("❌ 应用验证规则 {} (ID: {}) 时发生异常: {}", rule.getRuleName(), rule.getRuleId(), e.getMessage(), e);
                ruleResult.put("status", "error");
                ruleResult.put("message", "规则执行异常: " + e.getMessage());
                allValid = false;
            }
            
            ruleResults.add(ruleResult);
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
            
            // 评估表达式并设置字段值
            Object value = spelEvaluator.evaluate(rule.getRuleExpression(), invoice, null);
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
        
        for (int i = 0; i < invoice.getItems().size(); i++) {
            InvoiceItem item = invoice.getItems().get(i);
            Map<String, Object> itemResult = new HashMap<>();
            itemResult.put("item_index", i);
            itemResult.put("item_name", item.getName());
            
            try {
                // 构建包含当前商品的 SpEL 上下文
                Map<String, Object> services = Map.of(
                    "dbService", dbService,
                    "itemService", itemService,
                    "helper", spelHelper
                );
                
                Map<String, Object> context = spelHelper.buildSpelContext(invoice, item, services);
                
                // 评估表达式并设置字段值
                Object value = spelEvaluator.evaluate(rule.getRuleExpression(), invoice, item);
                setItemField(item, rule.getTargetField(), value);
                
                itemResult.put("status", "success");
                itemResult.put("field", rule.getTargetField());
                itemResult.put("value", value);
                itemResult.put("message", "字段补全成功");
                successCount++;
                
                log.debug("商品 {} 字段 {} 补全成功: {}", item.getName(), rule.getTargetField(), value);
                
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
        ruleResult.put("total_items", invoice.getItems().size());
        ruleResult.put("message", String.format("成功处理 %d/%d 个商品", successCount, invoice.getItems().size()));
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
        
        for (int i = 0; i < invoice.getItems().size(); i++) {
            InvoiceItem item = invoice.getItems().get(i);
            Map<String, Object> itemResult = new HashMap<>();
            itemResult.put("item_index", i);
            itemResult.put("item_name", item.getName());
            
            try {
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
        ruleResult.put("total_items", invoice.getItems().size());
        ruleResult.put("message", String.format("验证通过 %d/%d 个商品", validCount, invoice.getItems().size()));
        
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
     * 设置发票对象字段值
     * 
     * @param invoice 发票对象
     * @param fieldPath 字段路径 (如: invoice.supplier.taxNo)
     * @param value 字段值
     */
    private void setInvoiceField(InvoiceDomainObject invoice, String fieldPath, Object value) {
        try {
            // 移除 "invoice." 前缀
            String path = fieldPath.substring("invoice.".length());
            
            if (path.startsWith("supplier.")) {
                setSupplierField(invoice, path.substring("supplier.".length()), value);
            } else if (path.startsWith("customer.")) {
                setCustomerField(invoice, path.substring("customer.".length()), value);
            } else if (path.startsWith("extensions.")) {
                // 扩展字段
                if (invoice.getExtensions() == null) {
                    invoice.setExtensions(new HashMap<>());
                }
                String extField = path.substring("extensions.".length());
                invoice.getExtensions().put(extField, value);
                log.debug("设置发票扩展字段: {} = {}", extField, value);
            } else {
                // 直接的发票字段
                setDirectInvoiceField(invoice, path, value);
            }
            
        } catch (Exception e) {
            log.error("设置发票字段路径 {} 失败: {}", fieldPath, e.getMessage());
        }
    }
    
    /**
     * 设置供应商字段
     */
    private void setSupplierField(InvoiceDomainObject invoice, String fieldName, Object value) {
        if (invoice.getSupplier() == null) {
            invoice.setSupplier(new com.invoice.domain.Party());
        }
        
        switch (fieldName.toLowerCase()) {
            case "taxno":
                invoice.getSupplier().setTaxNo(spelHelper.toString(value));
                log.debug("设置供应商税号: {}", value);
                break;
            case "email":
                invoice.getSupplier().setEmail(spelHelper.toString(value));
                log.debug("设置供应商邮箱: {}", value);
                break;
            case "name":
                invoice.getSupplier().setName(spelHelper.toString(value));
                log.debug("设置供应商名称: {}", value);
                break;
            case "phone":
                invoice.getSupplier().setPhone(spelHelper.toString(value));
                log.debug("设置供应商电话: {}", value);
                break;
            default:
                log.warn("未知的供应商字段: {}", fieldName);
                break;
        }
    }
    
    /**
     * 设置客户字段
     */
    private void setCustomerField(InvoiceDomainObject invoice, String fieldName, Object value) {
        if (invoice.getCustomer() == null) {
            invoice.setCustomer(new com.invoice.domain.Party());
        }
        
        switch (fieldName.toLowerCase()) {
            case "taxno":
                invoice.getCustomer().setTaxNo(spelHelper.toString(value));
                log.debug("设置客户税号: {}", value);
                break;
            case "email":
                invoice.getCustomer().setEmail(spelHelper.toString(value));
                log.debug("设置客户邮箱: {}", value);
                break;
            case "name":
                invoice.getCustomer().setName(spelHelper.toString(value));
                log.debug("设置客户名称: {}", value);
                break;
            case "phone":
                invoice.getCustomer().setPhone(spelHelper.toString(value));
                log.debug("设置客户电话: {}", value);
                break;
            default:
                log.warn("未知的客户字段: {}", fieldName);
                break;
        }
    }
    
    /**
     * 设置直接的发票字段
     */
    private void setDirectInvoiceField(InvoiceDomainObject invoice, String fieldName, Object value) {
        switch (fieldName.toLowerCase()) {
            case "taxamount":
                if (value instanceof Number) {
                    invoice.setTaxAmount(spelHelper.toBigDecimal(value));
                    log.debug("设置发票税额: {}", value);
                }
                break;
            case "netamount":
            case "net_amount":
                if (value instanceof Number) {
                    invoice.setNetAmount(spelHelper.toBigDecimal(value));
                    log.debug("设置发票净额: {}", value);
                }
                break;
            case "invoicetype":
                invoice.setInvoiceType(spelHelper.toString(value));
                log.debug("设置发票类型: {}", value);
                break;
            case "country":
                // 国家字段设置到扩展字段中
                if (invoice.getExtensions() == null) {
                    invoice.setExtensions(new HashMap<>());
                }
                invoice.getExtensions().put("country", value);
                log.debug("设置发票国家: {}", value);
                break;
            default:
                log.warn("未知的发票字段: {}", fieldName);
                break;
        }
    }
    
    /**
     * 设置商品字段值
     * 
     * @param item 商品对象
     * @param fieldName 字段名
     * @param value 字段值
     */
    private void setItemField(InvoiceItem item, String fieldName, Object value) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return;
        }
        
        try {
            Object convertedValue = convertValue(value);
            
            // 根据字段名设置对应属性
            switch (fieldName.toLowerCase()) {
                case "quantity":
                    if (convertedValue instanceof Number) {
                        item.setQuantity(spelHelper.toBigDecimal(convertedValue));
                    }
                    break;
                case "unit_price":
                    if (convertedValue instanceof Number) {
                        item.setUnitPrice(spelHelper.toBigDecimal(convertedValue));
                    }
                    break;
                case "tax_rate":
                    if (convertedValue instanceof Number) {
                        item.setTaxRate(spelHelper.toBigDecimal(convertedValue));
                    }
                    break;
                case "name":
                    item.setName(spelHelper.toString(convertedValue));
                    break;
                case "description":
                    item.setDescription(spelHelper.toString(convertedValue));
                    break;
                case "category":
                    item.setCategory(spelHelper.toString(convertedValue));
                    break;
                default:
                    log.warn("未知的商品字段: {}", fieldName);
                    break;
            }
            
            log.debug("设置商品字段: {} = {}", fieldName, convertedValue);
            
        } catch (Exception e) {
            log.error("设置商品字段 {} 失败: {}", fieldName, e.getMessage());
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
        
        // 其他类型转换为字符串
        return value.toString();
    }
}