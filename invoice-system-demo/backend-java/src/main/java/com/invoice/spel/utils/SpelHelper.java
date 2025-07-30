package com.invoice.spel.utils;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.InvoiceItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SpEL 辅助工具类
 * 
 * 提供常用的表达式处理和数据转换功能：
 * - 数据类型转换
 * - 表达式预处理
 * - 上下文构建
 * - 字符串处理
 */
@Component
@Slf4j
public class SpelHelper {
    
    // 数据库查询表达式模式
    private static final Pattern DB_QUERY_PATTERN = Pattern.compile(
        "db\\.(\\w+)\\.(\\w+)\\[([^=]+)=([^\\]]+)\\]"
    );
    
    /**
     * 安全转换为 BigDecimal
     * 
     * @param value 输入值
     * @return BigDecimal 值或 null
     */
    public BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        
        try {
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            } else if (value instanceof Number) {
                return new BigDecimal(value.toString());
            } else if (value instanceof String) {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                return new BigDecimal(str);
            }
        } catch (NumberFormatException e) {
            log.warn("无法转换为 BigDecimal: {}", value);
        }
        
        return null;
    }
    
    /**
     * 安全转换为 Integer
     * 
     * @param value 输入值
     * @return Integer 值或 null
     */
    public Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        
        try {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                return Integer.parseInt(str);
            }
        } catch (NumberFormatException e) {
            log.warn("无法转换为 Integer: {}", value);
        }
        
        return null;
    }
    
    /**
     * 安全转换为 String
     * 
     * @param value 输入值
     * @return String 值或 null
     */
    public String toString(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof String) {
            String str = ((String) value).trim();
            return str.isEmpty() ? null : str;
        }
        
        return value.toString();
    }
    
    /**
     * 检查字符串是否为空或 null
     * 
     * @param value 字符串值
     * @return 是否为空
     */
    public boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
    
    /**
     * 检查字符串是否不为空
     * 
     * @param value 字符串值
     * @return 是否不为空
     */
    public boolean isNotEmpty(String value) {
        return !isEmpty(value);
    }
    
    /**
     * 计算百分比税率
     * 
     * @param taxRate 小数形式税率（如 0.13）
     * @return 百分比形式税率（如 13）
     */
    public BigDecimal toPercentage(BigDecimal taxRate) {
        if (taxRate == null) {
            return null;
        }
        
        return taxRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算小数税率
     * 
     * @param percentage 百分比形式税率（如 13）
     * @return 小数形式税率（如 0.13）
     */
    public BigDecimal toDecimal(BigDecimal percentage) {
        if (percentage == null) {
            return null;
        }
        
        return percentage.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算税额
     * 
     * @param amount 金额
     * @param taxRate 税率（小数形式）
     * @return 税额
     */
    public BigDecimal calculateTaxAmount(BigDecimal amount, BigDecimal taxRate) {
        if (amount == null || taxRate == null) {
            return null;
        }
        
        return amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算含税金额
     * 
     * @param amount 不含税金额
     * @param taxRate 税率（小数形式）
     * @return 含税金额
     */
    public BigDecimal calculateTaxInclusiveAmount(BigDecimal amount, BigDecimal taxRate) {
        if (amount == null || taxRate == null) {
            return amount;
        }
        
        BigDecimal taxAmount = calculateTaxAmount(amount, taxRate);
        return amount.add(taxAmount);
    }
    
    /**
     * 预处理数据库查询表达式
     * 
     * @param expression 原始表达式
     * @return 处理后的表达式
     */
    public String preprocessDbQuery(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return expression;
        }
        
        Matcher matcher = DB_QUERY_PATTERN.matcher(expression);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String table = matcher.group(1);
            String field = matcher.group(2);
            String condition = matcher.group(3);
            String value = matcher.group(4);
            
            // 转换为服务调用
            String replacement = String.format("@dbService.queryField('%s', '%s', '%s', %s)", 
                table, field, condition, value);
            
            matcher.appendReplacement(result, replacement);
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * 构建 SpEL 上下文
     * 
     * @param invoice 发票对象
     * @param item 商品明细（可选）
     * @param services 服务对象映射
     * @return SpEL 上下文
     */
    public Map<String, Object> buildSpelContext(InvoiceDomainObject invoice, 
                                               InvoiceItem item, 
                                               Map<String, Object> services) {
        Map<String, Object> context = new HashMap<>();
        
        // 添加发票对象
        if (invoice != null) {
            context.put("invoice", invoice);
        }
        
        // 添加商品明细
        if (item != null) {
            context.put("item", item);
        }
        
        // 添加服务对象
        if (services != null) {
            context.putAll(services);
        }
        
        // 添加辅助工具
        context.put("helper", this);
        
        return context;
    }
    
    /**
     * 转换为 CEL 兼容的 Map 上下文
     * 
     * @param invoice 发票对象
     * @param item 商品明细（可选）
     * @return CEL 兼容的 Map 上下文
     */
    public Map<String, Object> toCelCompatibleContext(InvoiceDomainObject invoice, InvoiceItem item) {
        Map<String, Object> context = new HashMap<>();
        
        if (invoice != null) {
            // 转换发票对象为 Map
            Map<String, Object> invoiceMap = new HashMap<>();
            invoiceMap.put("invoice_number", invoice.getInvoiceNumber());
            invoiceMap.put("issue_date", invoice.getIssueDate());
            invoiceMap.put("total_amount", invoice.getTotalAmount());
            invoiceMap.put("tax_amount", invoice.getTaxAmount());
            invoiceMap.put("currency", invoice.getCurrency());
            invoiceMap.put("country", invoice.getCountry());
            
            // 供应商信息
            if (invoice.getSupplier() != null) {
                Map<String, Object> supplierMap = new HashMap<>();
                supplierMap.put("name", invoice.getSupplier().getName());
                supplierMap.put("tax_no", invoice.getSupplier().getTaxNo());
                supplierMap.put("email", invoice.getSupplier().getEmail());
                supplierMap.put("company_type", invoice.getSupplier().getCompanyType());
                supplierMap.put("industry_classification", invoice.getSupplier().getIndustryClassification());
                invoiceMap.put("supplier", supplierMap);
            }
            
            // 客户信息
            if (invoice.getCustomer() != null) {
                Map<String, Object> customerMap = new HashMap<>();
                customerMap.put("name", invoice.getCustomer().getName());
                customerMap.put("tax_no", invoice.getCustomer().getTaxNo());
                customerMap.put("email", invoice.getCustomer().getEmail());
                customerMap.put("company_type", invoice.getCustomer().getCompanyType());
                customerMap.put("industry_classification", invoice.getCustomer().getIndustryClassification());
                invoiceMap.put("customer", customerMap);
            }
            
            context.put("invoice", invoiceMap);
        }
        
        if (item != null) {
            // 转换商品明细为 Map
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("name", item.getName());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("unit_price", item.getUnitPrice());
            itemMap.put("amount", item.getAmount());
            itemMap.put("tax_rate", item.getTaxRate());
            itemMap.put("tax_amount", item.getTaxAmount());
            itemMap.put("tax_category", item.getTaxCategory());
            
            context.put("item", itemMap);
        }
        
        return context;
    }
    
    /**
     * 格式化金额显示
     * 
     * @param amount 金额
     * @return 格式化后的金额字符串
     */
    public String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        
        return amount.setScale(2, RoundingMode.HALF_UP).toString();
    }
    
    /**
     * 格式化税率显示
     * 
     * @param taxRate 税率（小数形式）
     * @return 格式化后的税率字符串（百分比形式）
     */
    public String formatTaxRate(BigDecimal taxRate) {
        if (taxRate == null) {
            return "0%";
        }
        
        BigDecimal percentage = toPercentage(taxRate);
        return percentage.toString() + "%";
    }
    
    /**
     * 验证税号格式
     * 
     * @param taxNumber 税号
     * @return 是否有效
     */
    public boolean isValidTaxNumber(String taxNumber) {
        if (isEmpty(taxNumber)) {
            return false;
        }
        
        // 简单的税号格式验证（可根据实际需求调整）
        return taxNumber.matches("^[0-9A-Z]{15,20}$");
    }
    
    /**
     * 验证邮箱格式
     * 
     * @param email 邮箱
     * @return 是否有效
     */
    public boolean isValidEmail(String email) {
        if (isEmpty(email)) {
            return false;
        }
        
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}