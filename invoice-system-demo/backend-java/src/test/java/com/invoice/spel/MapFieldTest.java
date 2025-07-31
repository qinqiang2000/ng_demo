package com.invoice.spel;

import com.invoice.domain.InvoiceDomainObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MapFieldTest {

    private SpelFieldSetter spelFieldSetter;
    private InvoiceDomainObject invoice;

    @BeforeEach
    void setUp() {
        spelFieldSetter = new SpelFieldSetter();
        invoice = new InvoiceDomainObject();
        invoice.setExtensions(new HashMap<>());
    }

    @Test
    void testSetMapField() {
        // 测试设置 Map 字段
        spelFieldSetter.setFieldValue(invoice, "extensions.supplier_category", "TRAVEL_SERVICE");
        
        assertEquals("TRAVEL_SERVICE", invoice.getExtensions().get("supplier_category"));
    }

    @Test
    void testSetMapFieldWithDifferentTypes() {
        // 测试设置不同类型的 Map 字段值
        spelFieldSetter.setFieldValue(invoice, "extensions.supplier_category", "TRAVEL_SERVICE");
        spelFieldSetter.setFieldValue(invoice, "extensions.risk_level", "LOW");
        spelFieldSetter.setFieldValue(invoice, "extensions.approval_required", true);
        spelFieldSetter.setFieldValue(invoice, "extensions.amount_threshold", 1000);
        
        assertEquals("TRAVEL_SERVICE", invoice.getExtensions().get("supplier_category"));
        assertEquals("LOW", invoice.getExtensions().get("risk_level"));
        assertEquals(true, invoice.getExtensions().get("approval_required"));
        assertEquals(1000, invoice.getExtensions().get("amount_threshold"));
    }

    @Test
    void testSetMultipleMapFields() {
        // 测试设置多个 Map 字段
        spelFieldSetter.setFieldValue(invoice, "extensions.supplier_category", "TRAVEL_SERVICE");
        spelFieldSetter.setFieldValue(invoice, "extensions.risk_level", "LOW");
        spelFieldSetter.setFieldValue(invoice, "extensions.approval_required", true);
        
        assertEquals("TRAVEL_SERVICE", invoice.getExtensions().get("supplier_category"));
        assertEquals("LOW", invoice.getExtensions().get("risk_level"));
        assertEquals(true, invoice.getExtensions().get("approval_required"));
    }

    @Test
    void testRegularFieldStillWorks() {
        // 测试普通字段设置仍然正常工作
        spelFieldSetter.setFieldValue(invoice, "invoiceNumber", "12345");
        assertEquals("12345", invoice.getInvoiceNumber());
    }
}