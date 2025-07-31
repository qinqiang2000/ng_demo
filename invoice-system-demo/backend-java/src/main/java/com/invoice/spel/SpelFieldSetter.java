package com.invoice.spel;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * SpEL 字段设置器
 * 使用 SpEL 表达式直接设置对象字段值，消除硬编码的字段映射
 * 要求规则配置中的字段名与 Java 对象属性名完全一致（驼峰命名）
 */
@Component
@Slf4j
public class SpelFieldSetter {
    
    private final ExpressionParser parser = new SpelExpressionParser();
    private static final Pattern PROJECTION_PATTERN = Pattern.compile("^(.+)\\.\\!\\[(.+)\\]$");
    
    /**
     * 设置对象字段值
     * @param target 目标对象（可以是 InvoiceDomainObject、InvoiceItem 等）
     * @param fieldPath 字段路径（如 "taxAmount", "supplier.name", "items.![unitPrice]"）
     * @param value 要设置的值
     * @return 是否设置成功
     */
    public boolean setFieldValue(Object target, String fieldPath, Object value) {
        if (target == null || fieldPath == null || fieldPath.trim().isEmpty()) {
            log.warn("目标对象或字段路径为空");
            return false;
        }
        
        try {
            // 转换字段路径中的下划线命名为驼峰命名
            String normalizedFieldPath = normalizeFieldPath(fieldPath);
            log.debug("字段路径标准化: {} -> {}", fieldPath, normalizedFieldPath);
            
            if (isProjectionExpression(normalizedFieldPath)) {
                return setProjectionField(target, normalizedFieldPath, value);
            } else {
                return setRegularField(target, normalizedFieldPath, value);
            }
        } catch (Exception e) {
            log.error("设置字段 {} 失败: {}", fieldPath, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 标准化字段路径：将下划线命名转换为驼峰命名
     * 例如：supplier.tax_no -> supplier.taxNo
     */
    private String normalizeFieldPath(String fieldPath) {
        if (fieldPath == null || !fieldPath.contains("_")) {
            return fieldPath;
        }
        
        // 分割路径并转换每个部分
        String[] parts = fieldPath.split("\\.");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(".");
            }
            result.append(toCamelCase(parts[i]));
        }
        
        return result.toString();
    }
    
    /**
     * 将下划线命名转换为驼峰命名
     * 例如：tax_no -> taxNo, company_type -> companyType
     */
    private String toCamelCase(String underscoreName) {
        if (underscoreName == null || !underscoreName.contains("_")) {
            return underscoreName;
        }
        
        String[] parts = underscoreName.split("_");
        StringBuilder camelCase = new StringBuilder(parts[0]);
        
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].length() > 0) {
                camelCase.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    camelCase.append(parts[i].substring(1));
                }
            }
        }
        
        return camelCase.toString();
    }
    
    /**
     * 判断是否为投影表达式
     */
    private boolean isProjectionExpression(String fieldPath) {
        return PROJECTION_PATTERN.matcher(fieldPath).matches();
    }
    
    /**
     * 设置投影字段（如 items.![unitPrice]）
     */
    private boolean setProjectionField(Object target, String fieldPath, Object value) {
        var matcher = PROJECTION_PATTERN.matcher(fieldPath);
        if (!matcher.matches()) {
            return false;
        }
        
        String collectionPath = matcher.group(1);
        String fieldName = matcher.group(2);
        
        try {
            // 获取集合
            StandardEvaluationContext context = new StandardEvaluationContext(target);
            Expression collectionExpr = parser.parseExpression(collectionPath);
            Object collection = collectionExpr.getValue(context);
            
            if (collection instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) collection;
                
                // 检查 value 是否为列表，如果是则批量设置不同的值
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> valueList = (List<Object>) value;
                    
                    // 确保值列表的大小与集合大小匹配
                    int minSize = Math.min(list.size(), valueList.size());
                    
                    for (int i = 0; i < minSize; i++) {
                        Object item = list.get(i);
                        Object itemValue = valueList.get(i);
                        if (item != null) {
                            setRegularField(item, fieldName, itemValue);
                        }
                    }
                    
                    log.debug("批量设置投影字段 {} 完成，处理了 {} 个元素", fieldPath, minSize);
                    return true;
                } else {
                    // 为集合中的每个元素设置相同的值（原有逻辑）
                    for (Object item : list) {
                        if (item != null) {
                            setRegularField(item, fieldName, value);
                        }
                    }
                    log.debug("统一设置投影字段 {} 完成，处理了 {} 个元素", fieldPath, list.size());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("设置投影字段 {} 失败: {}", fieldPath, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 设置普通字段
     */
    private boolean setRegularField(Object target, String fieldPath, Object value) {
        try {
            // 检查是否为 Map 类型字段（如 extensions.supplier_category）
            if (isMapFieldPath(target, fieldPath)) {
                return setMapField(target, fieldPath, value);
            }
            
            StandardEvaluationContext context = new StandardEvaluationContext(target);
            Expression expr = parser.parseExpression(fieldPath);
            expr.setValue(context, value);
            return true;
        } catch (Exception e) {
            log.error("设置字段 {} 失败: {}", fieldPath, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查字段路径是否指向 Map 类型的字段
     * 使用反射判断字段类型，而不是硬编码字段名
     */
    private boolean isMapFieldPath(Object target, String fieldPath) {
        if (!fieldPath.contains(".")) {
            return false;
        }
        
        try {
            // 解析字段路径，获取第一级字段名（如 "extensions.supplier_category" -> "extensions"）
            String firstFieldName = fieldPath.split("\\.")[0];
            
            // 获取第一级字段的值
            StandardEvaluationContext context = new StandardEvaluationContext(target);
            Expression expr = parser.parseExpression(firstFieldName);
            Object fieldValue = expr.getValue(context);
            
            // 检查字段值是否为 Map 类型
            return fieldValue instanceof java.util.Map;
        } catch (Exception e) {
            log.debug("检查字段 {} 是否为 Map 类型时发生异常: {}", fieldPath, e.getMessage());
            return false;
        }
    }
    
    /**
     * 设置 Map 类型字段中的值
     * 通用方法，适用于所有 Map 类型字段，不仅仅是 extensions
     */
    @SuppressWarnings("unchecked")
    private boolean setMapField(Object target, String fieldPath, Object value) {
        try {
            // 解析字段路径，例如 "extensions.supplier_category" -> ["extensions", "supplier_category"]
            String[] pathParts = fieldPath.split("\\.", 2);
            if (pathParts.length != 2) {
                log.error("Map 字段路径格式不正确: {}", fieldPath);
                return false;
            }
            
            String mapFieldName = pathParts[0];
            String mapKey = pathParts[1];
            
            // 获取 Map 对象
            StandardEvaluationContext context = new StandardEvaluationContext(target);
            Expression mapExpr = parser.parseExpression(mapFieldName);
            Object mapObj = mapExpr.getValue(context);
            
            if (mapObj instanceof java.util.Map) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) mapObj;
                map.put(mapKey, value);
                log.debug("成功设置 Map 字段: {}.{} = {}", mapFieldName, mapKey, value);
                return true;
            } else {
                log.error("{} 字段不是 Map 类型: {}", mapFieldName, mapObj != null ? mapObj.getClass() : "null");
                return false;
            }
        } catch (Exception e) {
            log.error("设置 Map 字段 {} 失败: {}", fieldPath, e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证字段路径是否有效
     */
    public boolean isValidFieldPath(Object target, String fieldPath) {
        if (target == null || fieldPath == null || fieldPath.trim().isEmpty()) {
            return false;
        }
        
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(target);
            Expression expr = parser.parseExpression(fieldPath);
            // 尝试获取值来验证路径是否有效
            expr.getValue(context);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}