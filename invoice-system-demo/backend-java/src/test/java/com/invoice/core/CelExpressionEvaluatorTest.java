package com.invoice.core;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import com.invoice.domain.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CelExpressionEvaluatorTest {

    private CelExpressionEvaluator evaluator;
    private InvoiceDomainObject testInvoice;

    @BeforeEach
    void setUp() {
        evaluator = new CelExpressionEvaluator();
        
        // 创建测试发票对象
        testInvoice = new InvoiceDomainObject();
        testInvoice.setInvoiceNumber("INV-001");
        testInvoice.setIssueDate(LocalDate.now());
        testInvoice.setTotalAmount(new BigDecimal("1000.00"));
        testInvoice.setTaxAmount(new BigDecimal("130.00"));
        testInvoice.setCurrency("CNY");
        testInvoice.setStatus("DRAFT");
        
        // 设置供应商信息
        Party supplier = new Party();
        supplier.setName("测试供应商");
        supplier.setTaxNo("91110000123456789X");
        Address supplierAddress = Address.builder()
            .state("北京市")
            .city("朝阳区")
            .street("某某街道")
            .build();
        supplier.setAddress(supplierAddress);
        testInvoice.setSupplier(supplier);
        
        // 设置客户信息
        Party customer = new Party();
        customer.setName("测试客户");
        customer.setTaxNo("91110000987654321Y");
        Address customerAddress = Address.builder()
            .state("上海市")
            .city("浦东新区")
            .street("某某路")
            .build();
        customer.setAddress(customerAddress);
        testInvoice.setCustomer(customer);
    }

    @Test
    void testHasFunctionWithExistingField() {
        // 测试has()函数检查存在的字段
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        // 测试基本字段
        Boolean result = evaluator.evaluateBoolean("has(invoice, 'invoice_number')", context);
        assertTrue(result, "has()函数应该能检测到存在的invoice_number字段");
        
        // 测试供应商字段
        result = evaluator.evaluateBoolean("has(invoice.supplier, 'name')", context);
        assertTrue(result, "has()函数应该能检测到存在的supplier.name字段");
        
        // 测试客户字段
        result = evaluator.evaluateBoolean("has(invoice.customer, 'tax_no')", context);
        assertTrue(result, "has()函数应该能检测到存在的customer.tax_no字段");
    }

    @Test
    void testHasFunctionWithNonExistingField() {
        // 测试has()函数检查不存在的字段
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        Boolean result = evaluator.evaluateBoolean("has(invoice, 'non_existing_field')", context);
        assertFalse(result, "has()函数应该能检测到不存在的字段");
        
        result = evaluator.evaluateBoolean("has(invoice.supplier, 'non_existing_field')", context);
        assertFalse(result, "has()函数应该能检测到不存在的嵌套字段");
    }

    @Test
    void testHasFunctionWithExtensionsField() {
        // 测试has()函数检查extensions字段
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        // 测试extensions字段本身
        Boolean result = evaluator.evaluateBoolean("has(invoice, 'extensions')", context);
        assertTrue(result, "has()函数应该能检测到extensions字段");
        
        // 测试extensions中的supplier_category字段
        result = evaluator.evaluateBoolean("has(invoice.extensions, 'supplier_category')", context);
        assertTrue(result, "has()函数应该能检测到extensions.supplier_category字段");
        
        // 验证supplier_category的值
        String categoryValue = evaluator.evaluateString("invoice.extensions.supplier_category", context);
        assertEquals("TRAVEL_SERVICE", categoryValue, "supplier_category应该有正确的值");
    }

    @Test
    void testComplexHasExpression() {
        // 测试复杂的has()表达式（类似validation_005规则）
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        String expression = "has(invoice.extensions, 'supplier_category') && invoice.extensions.supplier_category == 'TRAVEL_SERVICE'";
        Boolean result = evaluator.evaluateBoolean(expression, context);
        assertTrue(result, "复杂的has()表达式应该能正确执行");
    }

    @Test
    void testFieldAccessWithoutHas() {
        // 测试直接字段访问（不使用has()函数）
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        String invoiceNumber = evaluator.evaluateString("invoice.invoice_number", context);
        assertEquals("INV-001", invoiceNumber, "应该能直接访问发票号码");
        
        String supplierName = evaluator.evaluateString("invoice.supplier.name", context);
        assertEquals("测试供应商", supplierName, "应该能直接访问供应商名称");
        
        BigDecimal totalAmount = evaluator.evaluateNumber("invoice.total_amount", context);
        assertEquals(new BigDecimal("1000.00"), totalAmount, "应该能直接访问总金额");
    }

    @Test
    void testDatabaseQuerySyntax() {
        // 测试数据库查询语法 db.companies.tax_number[name=invoice.supplier.name]
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        try {
            String expression = "db.companies.tax_number[name=invoice.supplier.name]";
            Object result = evaluator.evaluate(expression, context);
            
            System.out.println("数据库查询结果: " + result);
            assertNotNull(result, "数据库查询应该返回结果");
            
            // 由于是模拟数据，应该返回字符串类型的税号
            if (result instanceof String) {
                assertFalse(((String) result).isEmpty(), "税号不应该为空");
            }
            
        } catch (Exception e) {
            System.err.println("数据库查询语法测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("数据库查询语法测试失败: " + e.getMessage());
        }
    }

    @Test
    void testDatabaseQueryWithStringLiteral() {
        // 测试使用字符串字面量的数据库查询
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        try {
            String expression = "db.companies.category[name=\"示例公司A\"]";
            Object result = evaluator.evaluate(expression, context);
            
            System.out.println("字符串字面量查询结果: " + result);
            assertNotNull(result, "字符串字面量查询应该返回结果");
            
        } catch (Exception e) {
            System.err.println("字符串字面量测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("字符串字面量查询测试失败: " + e.getMessage());
        }
    }

    @Test
    void testTaxRateQuery() {
        // 测试税率查询
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        try {
            String expression = "db.tax_rates.rate[category=\"GENERAL\"]";
            Object result = evaluator.evaluate(expression, context);
            
            System.out.println("税率查询结果: " + result);
            assertNotNull(result, "税率查询应该返回结果");
            
            // 应该返回BigDecimal类型的税率
            if (result instanceof BigDecimal) {
                assertTrue(((BigDecimal) result).compareTo(BigDecimal.ZERO) > 0, "税率应该大于0");
            }
            
        } catch (Exception e) {
            System.err.println("税率查询测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("税率查询测试失败: " + e.getMessage());
        }
    }

    @Test
    void testComplexExpressionWithDatabaseQuery() {
        // 测试包含数据库查询的复杂表达式
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        try {
            String expression = "db.companies.tax_number[name=invoice.supplier.name] != \"\"";
            Object result = evaluator.evaluate(expression, context);
            
            System.out.println("复杂表达式结果: " + result);
            assertNotNull(result, "复杂表达式应该返回结果");
            assertTrue(result instanceof Boolean, "复杂表达式应该返回布尔值");
            
        } catch (Exception e) {
            System.err.println("复杂表达式测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("复杂表达式测试失败: " + e.getMessage());
        }
    }

    @Test
    void testDatabaseQueryPreprocessing() {
        // 测试数据库查询预处理功能
        Map<String, Object> context = evaluator.createContext(testInvoice, null, null);
        
        try {
            // 测试预处理是否正确替换了数据库查询语法
            String originalExpression = "db.companies.tax_number[name=invoice.supplier.name]";
            
            // 通过反射访问预处理方法（仅用于测试）
            java.lang.reflect.Method preprocessMethod = evaluator.getClass()
                .getDeclaredMethod("preprocessDatabaseQueries", String.class, Map.class);
            preprocessMethod.setAccessible(true);
            
            String processedExpression = (String) preprocessMethod.invoke(evaluator, originalExpression, context);
            
            System.out.println("原始表达式: " + originalExpression);
            System.out.println("预处理后表达式: " + processedExpression);
            
            assertNotEquals(originalExpression, processedExpression, "预处理应该修改表达式");
            assertFalse(processedExpression.contains("db."), "预处理后不应该包含db.语法");
            
        } catch (Exception e) {
            System.err.println("预处理测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("预处理测试失败: " + e.getMessage());
        }
    }
}