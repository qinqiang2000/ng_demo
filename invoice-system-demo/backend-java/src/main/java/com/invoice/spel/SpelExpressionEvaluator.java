package com.invoice.spel;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.InvoiceItem;
import com.invoice.spel.services.DbService;
import com.invoice.spel.services.ItemService;
import com.invoice.spel.utils.SpelHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Expression Language (SpEL) 表达式评估器
 * 
 * 支持 SpEL 标准语法，包括：
 * - 对象属性访问 (invoice.supplier.name)
 * - 方法调用 (@itemService.completeItemTaxRates())
 * - 数据库查询语法 (db.companies.tax_number[name=...])
 * - 逻辑和算术运算符
 * - 集合操作和过滤
 */
@Component
@Slf4j
public class SpelExpressionEvaluator {
    
    @Autowired
    private DbService dbService;
    
    @Autowired
    private ItemService itemService;
    
    @Autowired
    private SpelHelper spelHelper;
    
    private final ExpressionParser parser;
    
    // 数据库查询语法模式：db.table.field[condition=value]
    private static final Pattern DB_QUERY_PATTERN = Pattern.compile(
        "db\\.(\\w+)\\.(\\w+)\\[([^\\]]+)\\]"
    );
    
    public SpelExpressionEvaluator() {
        this.parser = new SpelExpressionParser();
    }
    
    /**
     * 评估 SpEL 表达式
     * 
     * @param expression SpEL 表达式字符串
     * @param invoice 发票对象
     * @param item 当前处理的发票明细项（可选）
     * @return 评估结果
     */
    public Object evaluate(String expression, InvoiceDomainObject invoice, InvoiceItem item) {
        try {
            log.debug("开始评估 SpEL 表达式: {}", expression);
            
            // 预处理表达式：移除 invoice. 前缀，因为根对象已经是 invoice
            String processedExpression = preprocessExpression(expression);
            log.debug("预处理后的表达式: {}", processedExpression);
            
            // 预处理数据库查询语法
            processedExpression = preprocessDatabaseQueries(processedExpression, invoice, item);
            log.debug("数据库查询预处理后的表达式: {}", processedExpression);
            
            // 创建评估上下文
            StandardEvaluationContext context = createEvaluationContext(invoice, item);
            
            // 解析并评估表达式
            Expression expr = parser.parseExpression(processedExpression);
            Object result = expr.getValue(context);
            
            log.debug("SpEL 表达式评估成功: {} = {}", expression, result);
            return result;
            
        } catch (Exception e) {
            log.error("SpEL 表达式评估失败: {}, 错误: {}", expression, e.getMessage(), e);
            throw new RuntimeException("SpEL 表达式评估失败: " + expression, e);
        }
    }
    
    /**
     * 预处理表达式：移除 invoice. 前缀，因为根对象已经是 invoice
     */
    private String preprocessExpression(String expression) {
        if (expression == null) {
            return null;
        }
        
        // 移除表达式开头的 invoice. 前缀
        String processed = expression.replaceAll("\\binvoice\\.", "");
        
        // 处理条件表达式中的 invoice. 前缀
        processed = processed.replaceAll("\\binvoice\\.(?!\\w*\\()", "");
        
        log.debug("表达式预处理: {} -> {}", expression, processed);
        return processed;
    }
    
    /**
     * 创建 SpEL 评估上下文
     */
    private StandardEvaluationContext createEvaluationContext(InvoiceDomainObject invoice, InvoiceItem item) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 设置根对象为发票
        context.setRootObject(invoice);
        
        // 注册变量
        context.setVariable("invoice", invoice);
        if (item != null) {
            context.setVariable("item", item);
        }
        
        // 注册 SpEL 服务 Bean
        context.setVariable("dbService", dbService);
        context.setVariable("itemService", itemService);
        context.setVariable("spelHelper", spelHelper);
        
        // 支持 @ 语法调用服务
        context.setBeanResolver((context1, beanName) -> {
            switch (beanName) {
                case "dbService":
                    return dbService;
                case "itemService":
                    return itemService;
                case "spelHelper":
                    return spelHelper;
                default:
                    return null;
            }
        });
        
        return context;
    }
    
    /**
     * 预处理数据库查询语法
     * 将 db.table.field[condition=value] 转换为实际查询结果
     * 复用CEL版本的成熟实现
     */
    private String preprocessDatabaseQueries(String expression, InvoiceDomainObject invoice, InvoiceItem item) {
        // 创建变量上下文
        Map<String, Object> variables = createVariableContext(invoice, item);
        
        // 匹配 db.table.field[conditions] 语法
        Pattern dbQueryPattern = Pattern.compile("db\\.([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\[([^\\]]+)\\]");
        Matcher matcher = dbQueryPattern.matcher(expression);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tableName = matcher.group(1);
            String fieldName = matcher.group(2);
            String conditions = matcher.group(3);
            
            log.debug("解析数据库查询: table={}, field={}, conditions={}", tableName, fieldName, conditions);
            
            // 执行智能查询并获取结果
            Object queryResult = executeSmartQuery(tableName, fieldName, conditions, variables);
            
            // 将查询结果转换为SpEL可识别的格式
            String replacement = convertValueToSpelLiteral(queryResult);
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * 创建变量上下文 - 优化版本，直接使用Bean对象
     */
    private Map<String, Object> createVariableContext(InvoiceDomainObject invoice, InvoiceItem item) {
        Map<String, Object> variables = new HashMap<>();
        
        if (invoice != null) {
            // 直接使用Bean对象，SPEL可以直接访问getter方法
            variables.put("invoice", invoice);
            log.info("createVariableContext: 直接使用Bean对象，invoice.supplier: {}", 
                invoice.getSupplier() != null ? invoice.getSupplier().getName() : "null");
        }
        
        if (item != null) {
            // 直接使用Bean对象
            variables.put("item", item);
        }
        
        log.info("createVariableContext: 最终variables包含的键: {}", variables.keySet());
        return variables;
    }

    /**
     * 执行智能查询（复用CEL版本的实现）
     */
    private Object executeSmartQuery(String tableName, String fieldName, String conditions, Map<String, Object> variables) {
        try {
            log.info("执行智能查询: table={}, field={}, conditions={}", tableName, fieldName, conditions);
            
            // 解析查询条件
            Map<String, Object> conditionMap = parseConditions(conditions, variables);
            
            // 根据表名执行不同的查询
            switch (tableName.toLowerCase()) {
                case "companies":
                    return queryCompanyField(fieldName, conditionMap);
                case "tax_rates":
                    return queryTaxRateField(fieldName, conditionMap);
                default:
                    log.warn("不支持的表名: {}", tableName);
                    return getDefaultValue(fieldName);
            }
            
        } catch (Exception e) {
            log.error("智能查询执行失败: table={}, field={}, conditions={}", tableName, fieldName, conditions, e);
            return getDefaultValue(fieldName);
        }
    }

    /**
     * 解析查询条件（复用CEL版本的实现）
     */
    private Map<String, Object> parseConditions(String conditions, Map<String, Object> variables) {
        Map<String, Object> conditionMap = new HashMap<>();
        
        // 支持多个条件，用逗号分隔
        String[] conditionPairs = conditions.split(",");
        
        for (String pair : conditionPairs) {
            String[] parts = pair.trim().split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                // 解析值表达式
                Object resolvedValue = resolveValue(value, variables);
                conditionMap.put(key, resolvedValue);
            }
        }
        
        return conditionMap;
    }

    /**
     * 解析值表达式
     */
    private Object resolveValue(String valueExpression, Map<String, Object> variables) {
        // 如果是字符串字面量
        if (valueExpression.startsWith("\"") && valueExpression.endsWith("\"")) {
            return valueExpression.substring(1, valueExpression.length() - 1);
        }
        
        // 如果是变量引用
        return getNestedValue(variables, valueExpression);
    }

    /**
     * 获取嵌套变量值 - 优化版本，支持Bean对象和Map
     */
    private Object getNestedValue(Map<String, Object> variables, String path) {
        log.debug("获取嵌套变量值: path={}, variables={}", path, variables.keySet());
        
        // 使用SPEL表达式来解析路径，这样可以直接访问Bean属性
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            
            // 设置变量到SPEL上下文
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
            
            // 如果路径不以#开头，添加#前缀（SPEL变量引用语法）
            String spelExpression = path.startsWith("#") ? path : "#" + path;
            
            // 如果路径不以已知的根变量开始，尝试添加 "invoice" 前缀
            String[] parts = path.split("\\.");
            if (parts.length > 0 && !variables.containsKey(parts[0])) {
                log.debug("路径 {} 不以根变量开始，尝试添加 invoice 前缀", path);
                if (variables.containsKey("invoice")) {
                    spelExpression = "#invoice." + path;
                }
            }
            
            log.debug("使用SPEL表达式: {}", spelExpression);
            
            Expression expression = parser.parseExpression(spelExpression);
            Object result = expression.getValue(context);
            
            log.debug("SPEL解析结果: {}", result);
            return result;
            
        } catch (Exception e) {
            log.debug("SPEL解析失败，回退到Map方式: {}", e.getMessage());
            
            // 回退到原来的Map方式（兼容性）
            return getNestedValueFromMap(variables, path);
        }
    }
    
    /**
     * 从Map中获取嵌套值（回退方法）
     */
    private Object getNestedValueFromMap(Map<String, Object> variables, String path) {
        String[] parts = path.split("\\.");
        Object current = variables;
        
        // 如果路径不是以已知的根变量开始，尝试添加 "invoice" 前缀
        if (parts.length > 0 && !variables.containsKey(parts[0])) {
            log.debug("路径 {} 不以根变量开始，尝试添加 invoice 前缀", path);
            if (variables.containsKey("invoice")) {
                current = variables.get("invoice");
                log.debug("使用 invoice 作为根对象: {}", current != null ? current.getClass().getSimpleName() : "null");
            }
        } else {
            // 如果路径以根变量开始，先获取根变量
            if (parts.length > 0 && variables.containsKey(parts[0])) {
                current = variables.get(parts[0]);
                log.debug("获取根变量 {}: {}", parts[0], current != null ? current.getClass().getSimpleName() : "null");
                // 跳过第一个部分，因为已经处理了
                parts = Arrays.copyOfRange(parts, 1, parts.length);
            }
        }
        
        for (String part : parts) {
            log.debug("处理路径部分: {}, 当前对象类型: {}", part, current != null ? current.getClass().getSimpleName() : "null");
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
                log.debug("从Map中获取值: key={}, value={}", part, current);
            } else {
                log.debug("当前对象不是Map，返回null");
                return null;
            }
        }
        
        log.debug("最终获取的值: {}", current);
        return current;
    }

    /**
     * 查询公司字段
     */
    private Object queryCompanyField(String fieldName, Map<String, Object> conditions) {
        try {
            // 使用DbService查询
            if (dbService != null && conditions.containsKey("name")) {
                String companyName = (String) conditions.get("name");
                Object result = dbService.queryField("companies", fieldName, "name", companyName);
                log.info("数据库查询结果: field={}, company={}, result={}", fieldName, companyName, result);
                
                if (result != null && !result.toString().isEmpty()) {
                    return result;
                }
                
                log.info("数据库查询无结果，返回默认值");
            } else {
                log.warn("DbService不可用或查询条件缺少name字段，返回默认值");
            }
            
            // 直接返回默认值
            return getDefaultValue(fieldName);
            
        } catch (Exception e) {
            log.error("公司查询失败: field={}, conditions={}", fieldName, conditions, e);
            return getDefaultValue(fieldName);
        }
    }

    /**
     * 查询税率字段
     */
    private Object queryTaxRateField(String fieldName, Map<String, Object> conditions) {
        // 模拟税率查询，支持多条件
        if ("rate".equals(fieldName)) {
            // 根据条件返回不同的税率
            if (conditions.containsKey("category")) {
                String category = (String) conditions.get("category");
                // 添加null检查，避免NullPointerException
                if (category == null) {
                    log.warn("税率查询条件中的category为null，使用默认税率");
                    return new BigDecimal("0.06"); // 默认税率6%
                }
                switch (category) {
                    case "TRAVEL_SERVICE":
                        return new BigDecimal("0.06");
                    case "GENERAL":
                        return new BigDecimal("0.13");
                    default:
                        return new BigDecimal("0.06");
                }
            }
            return new BigDecimal("0.06"); // 默认税率6%
        }
        return getDefaultValue(fieldName);
    }

    /**
     * 获取字段默认值
     */
    private Object getDefaultValue(String fieldName) {
        switch (fieldName.toLowerCase()) {
            case "tax_number":
            case "name":
                return "";
            case "category":
                return "GENERAL";
            case "rate":
                return new BigDecimal("0.06");
            default:
                return null;
        }
    }

    /**
     * 将值转换为SpEL字面量
     */
    private String convertValueToSpelLiteral(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "'" + value.toString().replace("'", "\\'") + "'";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else {
            return "'" + value.toString().replace("'", "\\'") + "'";
        }
    }

    /**
     * 将InvoiceDomainObject转换为Map
     */
    private Map<String, Object> convertInvoiceToMap(InvoiceDomainObject invoice) {
        Map<String, Object> invoiceMap = new HashMap<>();
        
        log.info("convertInvoiceToMap: 开始转换发票对象");
        log.info("发票对象: {}", invoice != null ? "非空" : "空");
        
        // 基本字段
        invoiceMap.put("invoice_number", invoice.getInvoiceNumber());
        invoiceMap.put("total_amount", invoice.getTotalAmount());
        invoiceMap.put("tax_amount", invoice.getTaxAmount());
        invoiceMap.put("net_amount", invoice.getNetAmount());
        invoiceMap.put("tax_rate", invoice.getTaxRate());
        invoiceMap.put("currency", invoice.getCurrency());
        
        // 供应商信息
        if (invoice.getSupplier() != null) {
            Map<String, Object> supplierMap = new HashMap<>();
            supplierMap.put("name", invoice.getSupplier().getName());
            supplierMap.put("tax_no", invoice.getSupplier().getTaxNo());
            supplierMap.put("email", invoice.getSupplier().getEmail());
            invoiceMap.put("supplier", supplierMap);
        } else {
            log.warn("供应商对象为空，跳过供应商信息转换");
        }
        
        // 客户信息
        log.info("客户对象: {}", invoice.getCustomer() != null ? "非空" : "空");
        if (invoice.getCustomer() != null) {
            log.info("客户名称: {}", invoice.getCustomer().getName());
            
            Map<String, Object> customerMap = new HashMap<>();
            customerMap.put("name", invoice.getCustomer().getName());
            customerMap.put("tax_no", invoice.getCustomer().getTaxNo());
            customerMap.put("email", invoice.getCustomer().getEmail());
            invoiceMap.put("customer", customerMap);
        }
        
        // 扩展字段
        if (invoice.getExtensions() != null) {
            invoiceMap.put("extensions", invoice.getExtensions());
        }
        
        log.info("最终invoiceMap包含的键: {}", invoiceMap.keySet());
        return invoiceMap;
    }

    /**
     * 将InvoiceItem转换为Map
     */
    private Map<String, Object> convertItemToMap(InvoiceItem item) {
        Map<String, Object> itemMap = new HashMap<>();
        
        itemMap.put("description", item.getDescription());
        itemMap.put("quantity", item.getQuantity());
        itemMap.put("unit_price", item.getUnitPrice());
        itemMap.put("amount", item.getAmount());
        
        return itemMap;
    }
    
    /**
     * 创建用于 CEL 兼容的 Map 上下文（如果需要混合使用）
     */
    public Map<String, Object> createMapContext(InvoiceDomainObject invoice, InvoiceItem item) {
        Map<String, Object> context = new HashMap<>();
        
        // 将发票对象转换为 Map 结构
        context.put("invoice", convertToMap(invoice));
        
        if (item != null) {
            context.put("item", convertToMap(item));
        }
        
        return context;
    }
    
    /**
     * 将对象转换为 Map 结构（用于 CEL 兼容性）
     */
    private Map<String, Object> convertToMap(Object obj) {
        // 这里可以使用 Jackson ObjectMapper 或反射来转换
        // 为简化实现，暂时返回空 Map
        return new HashMap<>();
    }
}