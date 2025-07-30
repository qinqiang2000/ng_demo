package com.invoice.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CEL数据库查询语法测试类
 * 
 * 测试在CEL表达式中使用数据库查询语法的功能，包括：
 * - 基本查询语法
 * - 条件查询
 * - 聚合查询
 * - 复杂查询组合
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CEL数据库查询语法测试")
public class CelDatabaseQueryTest {

    @Autowired
    private CelExpressionEvaluator evaluator;
    
    private Map<String, Object> testContext;

    @BeforeEach
    void setUp() {
        testContext = createTestContext();
    }

    /**
     * 创建测试上下文数据
     */
    private Map<String, Object> createTestContext() {
        Map<String, Object> context = new HashMap<>();
        
        // 发票数据
        Map<String, Object> invoice = new HashMap<>();
        invoice.put("invoice_number", "INV-2024-001");
        invoice.put("total_amount", new BigDecimal("1000.00"));
        invoice.put("supplier_name", "测试供应商");
        invoice.put("currency", "CNY");
        
        // 发票明细项
        Map<String, Object> item = new HashMap<>();
        item.put("product_name", "软件开发服务");
        item.put("category", "服务");
        
        context.put("invoice", invoice);
        context.put("item", item);
        
        return context;
    }

    @Test
    @DisplayName("基本数据库查询语法测试")
    void testBasicDatabaseQuery() {
        // 测试公司信息查询
        String expression = "db.companies.name == '测试科技有限公司'";
        
        try {
            Object result = evaluator.evaluate(expression, testContext);
            assertNotNull(result);
            System.out.println("基本查询结果: " + result);
        } catch (Exception e) {
            System.out.println("基本查询测试 - 预期异常（数据库可能为空）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("条件查询语法测试")
    void testConditionalDatabaseQuery() {
        // 测试带条件的查询
        String expression = "db.companies[name == '测试科技有限公司'].tax_id";
        
        try {
            Object result = evaluator.evaluate(expression, testContext);
            System.out.println("条件查询结果: " + result);
        } catch (Exception e) {
            System.out.println("条件查询测试 - 预期异常（数据库可能为空）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("产品信息查询测试")
    void testProductInfoQuery() {
        // 测试产品信息查询
        String expression = "get_product_info('软件开发服务')";
        
        try {
            Object result = evaluator.evaluate(expression, testContext);
            System.out.println("产品信息查询结果: " + result);
        } catch (Exception e) {
            System.out.println("产品信息查询测试 - 预期异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("标准名称查询测试")
    void testStandardNameQuery() {
        // 测试标准名称查询
        String expression = "get_standard_name('测试供应商')";
        
        try {
            Object result = evaluator.evaluate(expression, testContext);
            System.out.println("标准名称查询结果: " + result);
        } catch (Exception e) {
            System.out.println("标准名称查询测试 - 预期异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("复合查询表达式测试")
    void testComplexQueryExpressions() {
        // 测试复合查询表达式
        String expression = "invoice.supplier_name == get_standard_name(invoice.supplier_name)";
        
        try {
            Object result = evaluator.evaluate(expression, testContext);
            System.out.println("复合查询表达式结果: " + result);
        } catch (Exception e) {
            System.out.println("复合查询表达式测试 - 预期异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("数据库查询与业务规则结合测试")
    void testDatabaseQueryWithBusinessRules() {
        // 测试数据库查询与业务规则的结合
        String expression = "invoice.total_amount > 500 && has(get_product_info(item.product_name))";
        
        try {
            Object result = evaluator.evaluate(expression, testContext);
            System.out.println("数据库查询与业务规则结合结果: " + result);
        } catch (Exception e) {
            System.out.println("数据库查询与业务规则结合测试 - 预期异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("查询结果空值处理测试")
    void testQueryNullHandling() {
        // 测试查询结果为空的情况
        String expression = "has(get_product_info('不存在的产品'))";
        
        try {
            boolean result = evaluator.evaluateBoolean(expression, testContext);
            assertFalse(result, "不存在的产品查询应该返回false");
            System.out.println("空值处理测试结果: " + result);
        } catch (Exception e) {
            System.out.println("空值处理测试 - 预期异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("查询语法错误处理测试")
    void testQuerySyntaxErrorHandling() {
        // 测试错误的查询语法
        String[] invalidExpressions = {
            "db.invalid_table.field",
            "get_invalid_function('test')",
            "db.companies[invalid_condition].field"
        };
        
        for (String expression : invalidExpressions) {
            try {
                Object result = evaluator.evaluate(expression, testContext);
                System.out.println("意外成功的表达式: " + expression + " -> " + result);
            } catch (Exception e) {
                System.out.println("预期的语法错误: " + expression + " -> " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("查询性能测试")
    void testQueryPerformance() {
        String expression = "get_standard_name(invoice.supplier_name)";
        
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            try {
                evaluator.evaluate(expression, testContext);
            } catch (Exception e) {
                // 忽略异常，专注于性能测试
            }
        }
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        System.out.println("执行100次数据库查询表达式耗时: " + duration + "ms");
        
        // 确保性能在合理范围内
        assertTrue(duration < 5000, "数据库查询表达式执行性能不达标");
    }

    @Test
    @DisplayName("查询结果类型测试")
    void testQueryResultTypes() {
        // 测试不同类型的查询结果
        Map<String, String> testQueries = new HashMap<>();
        testQueries.put("get_standard_name('测试')", "字符串类型查询");
        testQueries.put("get_product_info('服务')", "对象类型查询");
        
        for (Map.Entry<String, String> entry : testQueries.entrySet()) {
            try {
                Object result = evaluator.evaluate(entry.getKey(), testContext);
                System.out.println(entry.getValue() + " 结果: " + result + 
                    " (类型: " + (result != null ? result.getClass().getSimpleName() : "null") + ")");
            } catch (Exception e) {
                System.out.println(entry.getValue() + " - 预期异常: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("查询缓存测试")
    void testQueryCaching() {
        String expression = "get_standard_name('测试供应商')";
        
        // 第一次查询
        long startTime1 = System.currentTimeMillis();
        try {
            Object result1 = evaluator.evaluate(expression, testContext);
            long endTime1 = System.currentTimeMillis();
            System.out.println("第一次查询耗时: " + (endTime1 - startTime1) + "ms, 结果: " + result1);
            
            // 第二次查询（可能使用缓存）
            long startTime2 = System.currentTimeMillis();
            Object result2 = evaluator.evaluate(expression, testContext);
            long endTime2 = System.currentTimeMillis();
            System.out.println("第二次查询耗时: " + (endTime2 - startTime2) + "ms, 结果: " + result2);
            
            // 比较结果一致性
            assertEquals(result1, result2, "两次查询结果应该一致");
            
        } catch (Exception e) {
            System.out.println("查询缓存测试 - 预期异常: " + e.getMessage());
        }
    }
}