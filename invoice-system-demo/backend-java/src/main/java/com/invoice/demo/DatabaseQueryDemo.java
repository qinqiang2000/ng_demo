package com.invoice.demo;

import com.invoice.core.CelExpressionEvaluator;
import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import com.invoice.domain.Address;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库查询语法演示
 * 展示如何在CEL表达式中使用自定义的数据库查询语法
 */
@Component
public class DatabaseQueryDemo {

    @Autowired
    private CelExpressionEvaluator evaluator;

    /**
     * 演示数据库查询语法的各种用法
     */
    public void demonstrateQueries() {
        InvoiceDomainObject invoice = createTestInvoice();
        
        System.out.println("=== 数据库查询语法演示 ===");
        System.out.println("测试发票供应商: " + invoice.getSupplier().getName());
        System.out.println("测试发票金额: " + invoice.getTotalAmount());
        System.out.println();
        
        // 1. 基本数据库查询语法 - 查询真实公司的税号
        String expression1 = "db.companies.tax_number[name=invoice.supplier.name]";
        Object result1 = evaluator.evaluate(expression1, invoice);
        System.out.println("1. 基本数据库查询:");
        System.out.println("   表达式: " + expression1);
        System.out.println("   结果: " + result1);
        System.out.println();
        
        // 2. 使用字符串字面量查询真实公司类别
        String expression2 = "db.companies.category[name=\"金蝶软件（中国）有限公司\"]";
        Object result2 = evaluator.evaluate(expression2, invoice);
        System.out.println("2. 查询指定公司类别:");
        System.out.println("   表达式: " + expression2);
        System.out.println("   结果: " + result2);
        System.out.println();
        
        // 3. 查询税率表
        String expression3 = "db.tax_rates.rate[category=\"GENERAL\"]";
        Object result3 = evaluator.evaluate(expression3, invoice);
        System.out.println("3. 查询税率:");
        System.out.println("   表达式: " + expression3);
        System.out.println("   结果: " + result3);
        System.out.println();
        
        // 4. 复杂表达式 - 使用真实税号
        String expression4 = "db.companies.tax_number[name=invoice.supplier.name] == \"91440300279156048U\" && invoice.total_amount > 1000.0";
        Object result4 = evaluator.evaluate(expression4, invoice);
        System.out.println("4. 复杂业务规则:");
        System.out.println("   表达式: " + expression4);
        System.out.println("   结果: " + result4);
        
        // 调试：分别查看各部分的结果
        String debugExpr1 = "db.companies.tax_number[name=invoice.supplier.name]";
        Object debugResult1 = evaluator.evaluate(debugExpr1, invoice);
        System.out.println("   调试 - 查询到的税号: " + debugResult1);
        
        String debugExpr2 = "invoice.total_amount > 1000.0";
        Object debugResult2 = evaluator.evaluate(debugExpr2, invoice);
        System.out.println("   调试 - 金额条件: " + debugResult2);
        System.out.println();
        
        // 5. 使用has()函数检查字段存在性
        String expression5 = "has(invoice, 'supplier') && db.companies.category[name=invoice.supplier.name] == \"TECH\"";
        Object result5 = evaluator.evaluate(expression5, invoice);
        System.out.println("5. 字段存在性检查 + 数据库查询:");
        System.out.println("   表达式: " + expression5);
        System.out.println("   结果: " + result5);
        
        // 调试：分别查看各部分的结果
        String debugExpr3 = "has(invoice, 'supplier')";
        Object debugResult3 = evaluator.evaluate(debugExpr3, invoice);
        System.out.println("   调试 - has函数结果: " + debugResult3);
        
        String debugExpr4 = "db.companies.category[name=invoice.supplier.name]";
        Object debugResult4 = evaluator.evaluate(debugExpr4, invoice);
        System.out.println("   调试 - 查询到的类别: " + debugResult4);
        System.out.println();
        
        System.out.println("=== 演示完成 ===");
    }
    
    /**
     * 创建测试发票数据
     */
    private InvoiceDomainObject createTestInvoice() {
        // 供应商信息 - 使用真实数据库中的公司
        Party supplier = Party.builder()
                .name("金蝶软件（中国）有限公司")
                .taxNo("91440300279156048U")
                .address(Address.builder()
                        .street("深圳市南山区科技中一路软件园2号楼")
                        .city("深圳")
                        .country("中国")
                        .build())
                .build();
        
        // 客户信息 - 使用真实数据库中的公司
        Party customer = Party.builder()
                .name("携程计算机技术（上海）有限公司")
                .taxNo("913100001332972H87")
                .address(Address.builder()
                        .street("上海市长宁区金钟路968号15楼")
                        .city("上海")
                        .country("中国")
                        .build())
                .build();
        
        return InvoiceDomainObject.builder()
                .invoiceNumber("INV-2024-001")
                .issueDate(LocalDate.now())
                .totalAmount(new BigDecimal("1500.00"))
                .supplier(supplier)
                .customer(customer)
                .build();
    }
}