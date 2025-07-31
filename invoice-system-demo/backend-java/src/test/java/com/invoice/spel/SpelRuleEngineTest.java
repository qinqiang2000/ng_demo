package com.invoice.spel;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.InvoiceItem;
import com.invoice.domain.Party;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpelRuleEngine 单元测试
 * 重点测试新增的核心功能：
 * 1. 从投影表达式中提取字段名
 * 2. 反射判断集合类型
 */
class SpelRuleEngineTest {

    private SpelRuleEngine spelRuleEngine;
    private InvoiceDomainObject testInvoice;
    private InvoiceItem testItem;

    @BeforeEach
    void setUp() {
        // 创建 SpelRuleEngine 实例
        spelRuleEngine = new SpelRuleEngine();
        
        // 创建测试数据
        testInvoice = new InvoiceDomainObject();
        testInvoice.setSupplier(new Party());
        testInvoice.getSupplier().setName("测试供应商");
        
        testItem = new InvoiceItem();
        testItem.setName("测试商品");
        testItem.setDescription("测试描述");
        
        List<InvoiceItem> items = new ArrayList<>();
        items.add(testItem);
        testInvoice.setItems(items);
    }

    /**
     * 测试从投影表达式中提取字段名的功能
     */
    @Test
    void testExtractFieldNameFromPath() throws Exception {
        // 使用反射调用私有方法
        Method extractMethod = SpelRuleEngine.class.getDeclaredMethod("extractFieldNameFromPath", String.class);
        extractMethod.setAccessible(true);

        // 测试用例1: SpEL 投影语法 - invoice.items.![name]
        String result1 = (String) extractMethod.invoke(spelRuleEngine, "invoice.items.![name]");
        assertEquals("name", result1, "应该从 'invoice.items.![name]' 中提取出 'name'");

        // 测试用例2: SpEL 投影语法 - items.![description]
        String result2 = (String) extractMethod.invoke(spelRuleEngine, "items.![description]");
        assertEquals("description", result2, "应该从 'items.![description]' 中提取出 'description'");

        // 测试用例3: 普通路径 - invoice.supplier.name
        String result3 = (String) extractMethod.invoke(spelRuleEngine, "invoice.supplier.name");
        assertEquals("name", result3, "应该从 'invoice.supplier.name' 中提取出 'name'");

        // 测试用例4: 简单字段名 - name
        String result4 = (String) extractMethod.invoke(spelRuleEngine, "name");
        assertEquals("name", result4, "简单字段名应该直接返回");

        // 测试用例5: 空值处理
        String result5 = (String) extractMethod.invoke(spelRuleEngine, "");
        assertEquals("", result5, "空字符串应该直接返回");

        String result6 = (String) extractMethod.invoke(spelRuleEngine, (String) null);
        assertNull(result6, "null 应该直接返回");

        // 测试用例6: 复杂投影语法 - invoice.items.![tax_rate]
        String result7 = (String) extractMethod.invoke(spelRuleEngine, "invoice.items.![tax_rate]");
        assertEquals("tax_rate", result7, "应该从 'invoice.items.![tax_rate]' 中提取出 'tax_rate'");
    }

    /**
     * 测试反射判断集合类型的功能（基础逻辑测试）
     */
    @Test
    void testIsCollectionTargetFieldBasicLogic() throws Exception {
        // 使用反射调用私有方法
        Method isCollectionMethod = SpelRuleEngine.class.getDeclaredMethod("isCollectionTargetField", String.class, InvoiceDomainObject.class);
        isCollectionMethod.setAccessible(true);

        // 测试用例1: 包含 items[] 的字段（回退逻辑）
        Boolean result1 = (Boolean) isCollectionMethod.invoke(spelRuleEngine, "items[]", testInvoice);
        assertTrue(result1, "'items[]' 应该通过回退逻辑被识别为集合类型");

        // 测试用例2: 包含投影操作符的字段
        Boolean result2 = (Boolean) isCollectionMethod.invoke(spelRuleEngine, "items.![name]", testInvoice);
        // 根据实际实现，包含 .![ 的表达式会被识别为集合类型
        assertTrue(result2, "'items.![name]' 包含投影操作符，应该被识别为集合类型");

        // 测试用例3: 明确的非集合字段
        Boolean result3 = (Boolean) isCollectionMethod.invoke(spelRuleEngine, "supplier", testInvoice);
        assertFalse(result3, "'supplier' 应该被识别为非集合类型");
    }

    /**
     * 测试 setItemField 方法处理投影语法
     * 由于 setItemField 依赖 SpelHelper，这里主要测试字段名提取逻辑
     */
    @Test
    void testSetItemFieldWithProjection() throws Exception {
        // 测试字段名提取逻辑
        Method extractMethod = SpelRuleEngine.class.getDeclaredMethod("extractFieldNameFromPath", String.class);
        extractMethod.setAccessible(true);

        // 测试用例1: 投影语法提取 name 字段
        String fieldName1 = (String) extractMethod.invoke(spelRuleEngine, "invoice.items.![name]");
        assertEquals("name", fieldName1, "应该正确提取 name 字段名");

        // 测试用例2: 投影语法提取 description 字段
        String fieldName2 = (String) extractMethod.invoke(spelRuleEngine, "items.![description]");
        assertEquals("description", fieldName2, "应该正确提取 description 字段名");

        // 测试用例3: 普通路径提取字段名
        String fieldName3 = (String) extractMethod.invoke(spelRuleEngine, "category");
        assertEquals("category", fieldName3, "应该正确提取 category 字段名");

        // 测试用例4: 数值字段名提取
        String fieldName4 = (String) extractMethod.invoke(spelRuleEngine, "items.![quantity]");
        assertEquals("quantity", fieldName4, "应该正确提取 quantity 字段名");

        // 测试用例5: 复杂路径字段名提取
        String fieldName5 = (String) extractMethod.invoke(spelRuleEngine, "invoice.items.![unit_price]");
        assertEquals("unit_price", fieldName5, "应该正确提取 unit_price 字段名");

        String fieldName6 = (String) extractMethod.invoke(spelRuleEngine, "items.![tax_rate]");
        assertEquals("tax_rate", fieldName6, "应该正确提取 tax_rate 字段名");
    }

    /**
     * 测试异常情况处理
     */
    @Test
    void testExceptionHandling() throws Exception {
        Method extractMethod = SpelRuleEngine.class.getDeclaredMethod("extractFieldNameFromPath", String.class);
        extractMethod.setAccessible(true);

        // 测试畸形的投影语法 - 根据实际实现逻辑
        String result1 = (String) extractMethod.invoke(spelRuleEngine, "items.![");
        // 实际实现中，如果不以 ] 结尾，会按普通路径处理，取最后一部分
        assertEquals("![", result1, "畸形投影语法应该按普通路径处理，返回最后一部分");

        String result2 = (String) extractMethod.invoke(spelRuleEngine, "items.]");
        assertEquals("]", result2, "畸形投影语法应该返回最后一部分");

        // 测试 isCollectionTargetField 的异常处理
        Method isCollectionMethod = SpelRuleEngine.class.getDeclaredMethod("isCollectionTargetField", String.class, InvoiceDomainObject.class);
        isCollectionMethod.setAccessible(true);

        // 传入 null 发票对象
        Boolean result3 = (Boolean) isCollectionMethod.invoke(spelRuleEngine, "items[]", null);
        assertTrue(result3, "null 发票对象时应该回退到字符串匹配逻辑");

        Boolean result4 = (Boolean) isCollectionMethod.invoke(spelRuleEngine, "supplier", null);
        assertFalse(result4, "null 发票对象时非集合字段应该返回 false");
    }

    /**
     * 测试边界情况
     */
    @Test
    void testEdgeCases() throws Exception {
        Method extractMethod = SpelRuleEngine.class.getDeclaredMethod("extractFieldNameFromPath", String.class);
        extractMethod.setAccessible(true);

        // 测试空的投影表达式
        String result1 = (String) extractMethod.invoke(spelRuleEngine, "items.![]");
        // 根据实际实现，items.![] 不会被识别为投影语法，按普通路径处理
        assertEquals("![]", result1, "items.![] 应该按普通路径处理，返回最后一部分");

        // 测试多层嵌套
        String result2 = (String) extractMethod.invoke(spelRuleEngine, "invoice.customer.addresses.![street]");
        assertEquals("street", result2, "多层嵌套应该正确提取字段名");

        // 测试只有点号
        String result3 = (String) extractMethod.invoke(spelRuleEngine, ".");
        assertEquals("", result3, "只有点号应该返回空字符串");

        // 测试多个连续点号
        String result4 = (String) extractMethod.invoke(spelRuleEngine, "invoice..items");
        assertEquals("items", result4, "多个连续点号应该返回最后一部分");

        // 测试复杂的投影表达式
        String result5 = (String) extractMethod.invoke(spelRuleEngine, "invoice.items.![unitPrice]");
        assertEquals("unitPrice", result5, "应该正确提取 unitPrice");

        String result6 = (String) extractMethod.invoke(spelRuleEngine, "items.![tax_rate]");
        assertEquals("tax_rate", result6, "应该正确提取 tax_rate");
    }

    /**
     * 测试字符串匹配回退逻辑
     */
    @Test
    void testStringMatchingFallback() throws Exception {
        Method isCollectionMethod = SpelRuleEngine.class.getDeclaredMethod("isCollectionTargetField", String.class, InvoiceDomainObject.class);
        isCollectionMethod.setAccessible(true);

        // 测试各种包含 items[] 的表达式
        assertTrue((Boolean) isCollectionMethod.invoke(spelRuleEngine, "items[]", testInvoice));
        assertTrue((Boolean) isCollectionMethod.invoke(spelRuleEngine, "invoice.items[]", testInvoice));
        assertTrue((Boolean) isCollectionMethod.invoke(spelRuleEngine, "some.items[].field", testInvoice));

        // 测试包含投影操作符的表达式
        assertTrue((Boolean) isCollectionMethod.invoke(spelRuleEngine, "items.![name]", testInvoice));
        assertTrue((Boolean) isCollectionMethod.invoke(spelRuleEngine, "invoice.items.![description]", testInvoice));

        // 测试不包含集合标识的表达式
        assertFalse((Boolean) isCollectionMethod.invoke(spelRuleEngine, "supplier", testInvoice));
        assertFalse((Boolean) isCollectionMethod.invoke(spelRuleEngine, "invoice.supplier.name", testInvoice));
        assertFalse((Boolean) isCollectionMethod.invoke(spelRuleEngine, "taxAmount", testInvoice));
    }
}