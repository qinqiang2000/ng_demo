package com.invoice.core;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import com.invoice.domain.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * has()函数功能演示
 * 
 * 演示如何使用has()函数来检查发票对象中字段的存在性，
 * 特别是针对validation_005规则的应用场景
 */
public class HasFunctionDemo {
    
    private CelExpressionEvaluator evaluator;
    private InvoiceDomainObject testInvoice;
    
    @BeforeEach
    void setUp() {
        evaluator = new CelExpressionEvaluator();
        
        // 创建测试发票对象
        testInvoice = InvoiceDomainObject.builder()
                .invoiceNumber("INV-2024-001")
                .issueDate(LocalDate.now())
                .totalAmount(new BigDecimal("1000.00"))
                .taxAmount(new BigDecimal("130.00"))
                .netAmount(new BigDecimal("870.00"))
                .currency("CNY")
                .status("PENDING")
                .invoiceType("STANDARD")
                .supplier(Party.builder()
                        .name("测试供应商")
                        .taxNo("91110000123456789X")
                        .address(Address.builder()
                                .street("测试街道123号")
                                .city("北京市")
                                .state("北京市")
                                .postalCode("100000")
                                .country("中国")
                                .build())
                        .phone("010-12345678")
                        .email("supplier@test.com")
                        .build())
                .customer(Party.builder()
                        .name("测试客户")
                        .taxNo("91110000987654321Y")
                        .address(Address.builder()
                                .street("客户街道456号")
                                .city("上海市")
                                .state("上海市")
                                .postalCode("200000")
                                .country("中国")
                                .build())
                        .phone("021-87654321")
                        .email("customer@test.com")
                        .build())
                .build();
    }
    
    @Test
    void demonstrateValidation005Rule() {
        System.out.println("=== has()函数功能演示 ===");
        
        Map<String, Object> context = evaluator.createContext(testInvoice);
        
        // 演示1：检查基本字段存在性
        System.out.println("\n1. 检查基本字段存在性：");
        
        boolean hasInvoiceNumber = evaluator.evaluateBoolean("has(invoice, 'invoice_number')", context);
        System.out.println("   has(invoice, 'invoice_number') = " + hasInvoiceNumber);
        assertTrue(hasInvoiceNumber);
        
        boolean hasTotalAmount = evaluator.evaluateBoolean("has(invoice, 'total_amount')", context);
        System.out.println("   has(invoice, 'total_amount') = " + hasTotalAmount);
        assertTrue(hasTotalAmount);
        
        // 演示2：检查嵌套字段存在性
        System.out.println("\n2. 检查嵌套字段存在性：");
        
        boolean hasSupplier = evaluator.evaluateBoolean("has(invoice, 'supplier')", context);
        System.out.println("   has(invoice, 'supplier') = " + hasSupplier);
        assertTrue(hasSupplier);
        
        boolean hasSupplierName = evaluator.evaluateBoolean("has(invoice.supplier, 'name')", context);
        System.out.println("   has(invoice.supplier, 'name') = " + hasSupplierName);
        assertTrue(hasSupplierName);
        
        boolean hasSupplierTaxNo = evaluator.evaluateBoolean("has(invoice.supplier, 'tax_no')", context);
        System.out.println("   has(invoice.supplier, 'tax_no') = " + hasSupplierTaxNo);
        assertTrue(hasSupplierTaxNo);
        
        // 演示3：检查不存在的字段
        System.out.println("\n3. 检查不存在的字段：");
        
        boolean hasNonExisting = evaluator.evaluateBoolean("has(invoice, 'non_existing_field')", context);
        System.out.println("   has(invoice, 'non_existing_field') = " + hasNonExisting);
        assertFalse(hasNonExisting);
        
        boolean hasSupplierNonExisting = evaluator.evaluateBoolean("has(invoice.supplier, 'non_existing_field')", context);
        System.out.println("   has(invoice.supplier, 'non_existing_field') = " + hasSupplierNonExisting);
        assertFalse(hasSupplierNonExisting);
        
        // 演示4：检查extensions字段（模拟validation_005场景）
        System.out.println("\n4. 检查extensions字段（validation_005场景）：");
        
        boolean hasExtensions = evaluator.evaluateBoolean("has(invoice, 'extensions')", context);
        System.out.println("   has(invoice, 'extensions') = " + hasExtensions);
        assertTrue(hasExtensions);
        
        boolean hasSupplierCategory = evaluator.evaluateBoolean("has(invoice.extensions, 'supplier_category')", context);
        System.out.println("   has(invoice.extensions, 'supplier_category') = " + hasSupplierCategory);
        assertTrue(hasSupplierCategory);
        
        // 演示5：复杂的validation_005规则表达式
        System.out.println("\n5. 复杂的validation_005规则表达式：");
        
        String validation005Expression = "has(invoice.extensions, 'supplier_category') && invoice.extensions.supplier_category == 'TRAVEL_SERVICE'";
        boolean validation005Result = evaluator.evaluateBoolean(validation005Expression, context);
        System.out.println("   表达式: " + validation005Expression);
        System.out.println("   结果: " + validation005Result);
        assertTrue(validation005Result);
        
        // 演示6：组合条件检查
        System.out.println("\n6. 组合条件检查：");
        
        String complexExpression = "has(invoice, 'supplier') && has(invoice.supplier, 'tax_no') && invoice.supplier.tax_no != ''";
        boolean complexResult = evaluator.evaluateBoolean(complexExpression, context);
        System.out.println("   表达式: " + complexExpression);
        System.out.println("   结果: " + complexResult);
        assertTrue(complexResult);
        
        System.out.println("\n=== 演示完成 ===");
        System.out.println("✅ has()函数已成功实现并可以正常使用！");
        System.out.println("✅ validation_005规则现在可以正确执行！");
    }
}