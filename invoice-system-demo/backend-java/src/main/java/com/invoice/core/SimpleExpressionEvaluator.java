package com.invoice.core;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import com.invoice.domain.Address;
import com.invoice.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单表达式求值器
 * 
 * 支持 CEL 风格的表达式求值，包括：
 * - 字段路径访问
 * - 比较和逻辑运算符
 * - 基本函数调用
 * - 数据库查询语法
 */
@Component
public class SimpleExpressionEvaluator {
    
    private static final Logger log = LoggerFactory.getLogger(SimpleExpressionEvaluator.class);
    
    @Autowired
    private CompanyRepository companyRepository;

    private static final Pattern FIELD_PATH_PATTERN = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d*\\.?\\d+");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'");
    
    /**
     * 求值表达式
     * 
     * @param expression 表达式
     * @param context 上下文变量
     * @return 求值结果
     */
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        
        try {
            log.debug("求值表达式: {}", expression);
            return evaluateExpression(expression.trim(), context);
        } catch (Exception e) {
            log.error("表达式求值失败: {}", expression, e);
            throw new RuntimeException("表达式求值失败: " + expression, e);
        }
    }
    
    /**
     * 求值表达式（内部实现）
     */
    private Object evaluateExpression(String expr, Map<String, Object> context) {
        // 处理逻辑非操作符 !
        if (expr.startsWith("!")) {
            String innerExpr = expr.substring(1).trim();
            Object result = evaluateExpression(innerExpr, context);
            return !isTrue(result);
        }
        
        // 处理逻辑运算符 OR
        if (expr.contains(" OR ")) {
            String[] parts = expr.split(" OR ", 2);
            Object left = evaluateExpression(parts[0].trim(), context);
            if (isTrue(left)) {
                return true;
            }
            Object right = evaluateExpression(parts[1].trim(), context);
            return isTrue(right);
        }
        
        // 处理逻辑运算符 AND
        if (expr.contains(" AND ")) {
            String[] parts = expr.split(" AND ", 2);
            Object left = evaluateExpression(parts[0].trim(), context);
            if (!isTrue(left)) {
                return false;
            }
            Object right = evaluateExpression(parts[1].trim(), context);
            return isTrue(right);
        }
        
        // 处理比较运算符
        for (String op : new String[]{"==", "!=", "<=", ">=", "<", ">"}) {
            if (expr.contains(" " + op + " ")) {
                String[] parts = expr.split(" " + Pattern.quote(op) + " ", 2);
                Object left = evaluateExpression(parts[0].trim(), context);
                Object right = evaluateExpression(parts[1].trim(), context);
                return evaluateComparison(left, right, op);
            }
        }
        
        // 处理算术运算符
        for (String op : new String[]{"*", "/", "+", "-"}) {
            if (expr.contains(" " + op + " ")) {
                String[] parts = expr.split(" " + Pattern.quote(op) + " ", 2);
                Object left = evaluateExpression(parts[0].trim(), context);
                Object right = evaluateExpression(parts[1].trim(), context);
                return evaluateArithmetic(left, right, op);
            }
        }
        
        // 处理函数调用
        if (expr.contains("(") && expr.endsWith(")")) {
            int openParen = expr.indexOf('(');
            String funcName = expr.substring(0, openParen).trim();
            String args = expr.substring(openParen + 1, expr.length() - 1).trim();
            return evaluateFunction(funcName, args, context);
        }
        
        // 处理字面量
        Object literal = parseLiteral(expr);
        if (literal != null) {
            return literal;
        }
        
        // 处理字段路径
        return getFieldValue(expr, context);
    }
    
    /**
     * 比较运算
     */
    private Object evaluateComparison(Object left, Object right, String operator) {
        if (left == null && right == null) {
            return "==".equals(operator) || "<=".equals(operator) || ">=".equals(operator);
        }
        if (left == null || right == null) {
            return "!=".equals(operator);
        }
        
        // 数值比较
        if (left instanceof Number && right instanceof Number) {
            BigDecimal leftNum = toBigDecimal((Number) left);
            BigDecimal rightNum = toBigDecimal((Number) right);
            int compareResult = leftNum.compareTo(rightNum);
            
            switch (operator) {
                case "==": return compareResult == 0;
                case "!=": return compareResult != 0;
                case "<": return compareResult < 0;
                case "<=": return compareResult <= 0;
                case ">": return compareResult > 0;
                case ">=": return compareResult >= 0;
                default: return false;
            }
        }
        
        // 字符串比较
        String leftStr = String.valueOf(left);
        String rightStr = String.valueOf(right);
        int compareResult = leftStr.compareTo(rightStr);
        
        switch (operator) {
            case "==": return compareResult == 0;
            case "!=": return compareResult != 0;
            case "<": return compareResult < 0;
            case "<=": return compareResult <= 0;
            case ">": return compareResult > 0;
            case ">=": return compareResult >= 0;
            default: return false;
        }
    }
    
    /**
     * 算术运算
     */
    private Object evaluateArithmetic(Object left, Object right, String operator) {
        if (!(left instanceof Number) || !(right instanceof Number)) {
            throw new IllegalArgumentException("算术运算要求数值类型");
        }
        
        BigDecimal leftNum = toBigDecimal((Number) left);
        BigDecimal rightNum = toBigDecimal((Number) right);
        
        switch (operator) {
            case "+": return leftNum.add(rightNum);
            case "-": return leftNum.subtract(rightNum);
            case "*": return leftNum.multiply(rightNum);
            case "/": 
                if (rightNum.compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("除数不能为零");
                }
                return leftNum.divide(rightNum, 6, RoundingMode.HALF_UP);
            default: return BigDecimal.ZERO;
        }
    }
    
    /**
     * 函数调用
     */
    private Object evaluateFunction(String funcName, String args, Map<String, Object> context) {
        switch (funcName.toLowerCase()) {
            case "has":
                return evaluateHasFunction(args, context);
            case "len":
                return evaluateLenFunction(args, context);
            case "format_date":
                return evaluateFormatDateFunction(args, context);
            case "round":
                return evaluateRoundFunction(args, context);
            case "upper":
                return evaluateUpperFunction(args, context);
            case "lower":
                return evaluateLowerFunction(args, context);
            case "get_tax_rate":
                return evaluateGetTaxRateFunction(args, context);
            default:
                throw new IllegalArgumentException("未知函数: " + funcName);
        }
    }
    
    /**
     * has() 函数 - 检查字段是否存在且非空
     */
    private Object evaluateHasFunction(String args, Map<String, Object> context) {
        Object value = evaluateExpression(args, context);
        return value != null && !String.valueOf(value).trim().isEmpty();
    }
    
    /**
     * len() 函数 - 获取字符串长度
     */
    private Object evaluateLenFunction(String args, Map<String, Object> context) {
        Object value = evaluateExpression(args, context);
        if (value == null) {
            return 0;
        }
        return String.valueOf(value).length();
    }
    
    /**
     * format_date() 函数 - 格式化日期
     */
    private Object evaluateFormatDateFunction(String args, Map<String, Object> context) {
        // 简化实现，后续可扩展
        Object value = evaluateExpression(args, context);
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return String.valueOf(value);
    }
    
    /**
     * round() 函数 - 数值四舍五入
     */
    private Object evaluateRoundFunction(String args, Map<String, Object> context) {
        Object value = evaluateExpression(args, context);
        if (value instanceof Number) {
            BigDecimal num = toBigDecimal((Number) value);
            return num.setScale(2, RoundingMode.HALF_UP);
        }
        return value;
    }
    
    /**
     * upper() 函数 - 转大写
     */
    private Object evaluateUpperFunction(String args, Map<String, Object> context) {
        Object value = evaluateExpression(args, context);
        return value == null ? null : String.valueOf(value).toUpperCase();
    }
    
    /**
     * lower() 函数 - 转小写
     */
    private Object evaluateLowerFunction(String args, Map<String, Object> context) {
        Object value = evaluateExpression(args, context);
        return value == null ? null : String.valueOf(value).toLowerCase();
    }
    
    /**
     * get_tax_rate() 函数 - 根据商品描述获取税率
     */
    private Object evaluateGetTaxRateFunction(String args, Map<String, Object> context) {
        Object value = evaluateExpression(args, context);
        if (value == null) {
            return new BigDecimal("0.13"); // 默认税率13%
        }
        
        String description = String.valueOf(value).toLowerCase();
        
        // 根据商品描述判断税率
        if (description.contains("食品") || description.contains("农产品")) {
            return new BigDecimal("0.09"); // 9%税率
        } else if (description.contains("图书") || description.contains("报纸") || description.contains("杂志")) {
            return new BigDecimal("0.09"); // 9%税率
        } else if (description.contains("交通") || description.contains("运输")) {
            return new BigDecimal("0.09"); // 9%税率
        } else if (description.contains("基础电信") || description.contains("邮政")) {
            return new BigDecimal("0.09"); // 9%税率
        } else if (description.contains("建筑") || description.contains("不动产")) {
            return new BigDecimal("0.09"); // 9%税率
        } else {
            return new BigDecimal("0.13"); // 一般税率13%
        }
    }
    
    /**
     * 解析字面量
     */
    private Object parseLiteral(String expr) {
        // 字符串字面量
        Matcher stringMatcher = STRING_PATTERN.matcher(expr);
        if (stringMatcher.matches()) {
            String content = stringMatcher.group(1);
            return content != null ? content : stringMatcher.group(2);
        }
        
        // 数值字面量
        Matcher numberMatcher = NUMBER_PATTERN.matcher(expr);
        if (numberMatcher.matches()) {
            if (expr.contains(".")) {
                return new BigDecimal(expr);
            } else {
                return Integer.parseInt(expr);
            }
        }
        
        // 布尔值
        if ("true".equalsIgnoreCase(expr)) {
            return true;
        }
        if ("false".equalsIgnoreCase(expr)) {
            return false;
        }
        
        return null;
    }
    
    /**
     * 获取字段值
     */
    private Object getFieldValue(String fieldPath, Map<String, Object> context) {
        // 处理数据库查询语法: db.companies.tax_number[name=invoice.supplier.name]
        if (fieldPath.startsWith("db.")) {
            return evaluateDatabaseQuery(fieldPath, context);
        }
        
        String[] pathParts = fieldPath.split("\\.");
        Object current = context.get(pathParts[0]);
        
        if (current == null) {
            return null;
        }
        
        for (int i = 1; i < pathParts.length; i++) {
            current = getObjectProperty(current, pathParts[i]);
            if (current == null) {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * 处理数据库查询表达式
     * 支持语法: db.companies.field[condition=value]
     */
    private Object evaluateDatabaseQuery(String queryExpr, Map<String, Object> context) {
        try {
            log.debug("处理数据库查询: {}", queryExpr);
            
            // 解析查询表达式: db.companies.tax_number[name=invoice.supplier.name]
            Pattern dbQueryPattern = Pattern.compile("db\\.(\\w+)\\.(\\w+)\\[([^\\]]+)\\]");
            Matcher matcher = dbQueryPattern.matcher(queryExpr);
            
            if (!matcher.matches()) {
                log.warn("不支持的数据库查询语法: {}", queryExpr);
                return null;
            }
            
            String tableName = matcher.group(1);
            String fieldName = matcher.group(2);
            String condition = matcher.group(3);
            
            // 目前只支持companies表
            if (!"companies".equals(tableName)) {
                log.warn("不支持的数据库表: {}", tableName);
                return null;
            }
            
            // 解析条件: name=invoice.supplier.name
            String[] conditionParts = condition.split("=", 2);
            if (conditionParts.length != 2) {
                log.warn("不支持的查询条件格式: {}", condition);
                return null;
            }
            
            String conditionField = conditionParts[0].trim();
            String conditionValue = conditionParts[1].trim();
            
            // 求值条件值（可能是表达式）
            Object actualValue = evaluateExpression(conditionValue, context);
            if (actualValue == null) {
                log.debug("查询条件值为null: {}", conditionValue);
                return null;
            }
            
            String valueStr = String.valueOf(actualValue);
            log.debug("执行数据库查询: table={}, field={}, condition={}={}", 
                     tableName, fieldName, conditionField, valueStr);
            
            // 调用CompanyRepository的智能查询方法
            List<String> results = companyRepository.findFieldValueByCondition(fieldName, valueStr);
            
            if (results != null && !results.isEmpty()) {
                String result = results.get(0);
                log.debug("数据库查询结果: {}", result);
                return result;
            } else {
                log.debug("数据库查询无结果");
                return null;
            }
            
        } catch (Exception e) {
            log.error("数据库查询失败: {}", queryExpr, e);
            return null;
        }
    }
    
    /**
     * 获取对象属性
     */
    private Object getObjectProperty(Object obj, String property) {
        if (obj == null) {
            return null;
        }
        
        try {
            // 处理发票域对象
            if (obj instanceof InvoiceDomainObject) {
                InvoiceDomainObject invoice = (InvoiceDomainObject) obj;
                switch (property) {
                    case "invoice_number": return invoice.getInvoiceNumber();
                    case "total_amount": return invoice.getTotalAmount();
                    case "tax_amount": return invoice.getTaxAmount();
                    case "supplier": return invoice.getSupplier();
                    case "customer": return invoice.getCustomer();
                    case "issue_date": return invoice.getIssueDate();
                    case "due_date": return invoice.getDueDate();
                    case "currency": return invoice.getCurrency();
                    case "notes": return invoice.getNotes();
                    case "status": return invoice.getStatus();
                    default: return null;
                }
            }
            
            // 处理 Party 对象
            if (obj instanceof Party) {
                Party party = (Party) obj;
                switch (property) {
                    case "name": return party.getName();
                    case "standard_name": return party.getStandardName();
                    case "tax_no": return party.getTaxNo();
                    case "address": return party.getAddress();
                    case "phone": return party.getPhone();
                    case "email": return party.getEmail();
                    case "contact_person": return party.getLegalRepresentative();
                    case "bank_account": return party.getBankAccount();
                    case "bank_name": return party.getBankName();
                    default: return null;
                }
            }
            
            // 处理地址对象
            if (obj instanceof Address) {
                Address address = (Address) obj;
                switch (property) {
                    case "street": return address.getStreet();
                    case "city": return address.getCity();
                    case "province": return address.getState();
                    case "postal_code": return address.getPostalCode();
                    case "country": return address.getCountry();
                    default: return null;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("获取对象属性失败: {} -> {}", obj.getClass().getSimpleName(), property, e);
            return null;
        }
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
     * 转换为 BigDecimal
     */
    private BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        return BigDecimal.valueOf(number.doubleValue());
    }
    
    /**
     * 创建上下文
     */
    public Map<String, Object> createContext(InvoiceDomainObject invoice) {
        Map<String, Object> context = new HashMap<>();
        context.put("invoice", invoice);
        return context;
    }
}