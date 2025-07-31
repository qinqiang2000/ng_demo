package com.invoice.spel;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

/**
 * SpelFieldSetter 测试
 * 验证统一驼峰命名方案的字段设置功能
 */
public class SpelFieldSetterTest {
    
    private SpelFieldSetter spelFieldSetter;
    private InvoiceDomainObject invoice;
    
    @BeforeEach
    void setUp() {
        spelFieldSetter = new SpelFieldSetter();
        invoice = new InvoiceDomainObject();
        invoice.setSupplier(new Party());
        invoice.setCustomer(new Party());
    }
    
    @Test
    void testSetSimpleField() {
        // 测试设置简单字段（驼峰命名）
        boolean result = spelFieldSetter.setFieldValue(invoice, "country", "CN");
        assertTrue(result);
        assertEquals("CN", invoice.getCountry());
    }
    
    @Test
    void testSetNestedField() {
        // 测试设置嵌套字段（驼峰命名）
        boolean result = spelFieldSetter.setFieldValue(invoice, "supplier.taxNo", "123456789012345ABC");
        assertTrue(result);
        assertEquals("123456789012345ABC", invoice.getSupplier().getTaxNo());
    }
    
    @Test
    void testSetNumericField() {
        // 测试设置数值字段（驼峰命名）
        boolean result = spelFieldSetter.setFieldValue(invoice, "taxAmount", new BigDecimal("100.00"));
        assertTrue(result);
        assertEquals(new BigDecimal("100.00"), invoice.getTaxAmount());
    }
    
    @Test
    void testInvalidFieldPath() {
        // 测试无效字段路径
        boolean result = spelFieldSetter.setFieldValue(invoice, "nonExistentField", "value");
        assertFalse(result);
    }
    
    @Test
    void testNullTarget() {
        // 测试空目标对象
        boolean result = spelFieldSetter.setFieldValue(null, "country", "CN");
        assertFalse(result);
    }
    
    @Test
    void testEmptyFieldPath() {
        // 测试空字段路径
        boolean result = spelFieldSetter.setFieldValue(invoice, "", "value");
        assertFalse(result);
    }
}