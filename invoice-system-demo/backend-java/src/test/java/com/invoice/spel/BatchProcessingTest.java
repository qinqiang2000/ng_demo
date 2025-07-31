package com.invoice.spel;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.InvoiceItem;
import com.invoice.models.BusinessRule;
import com.invoice.spel.services.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 批量处理功能测试
 */
@SpringBootTest
public class BatchProcessingTest {

    @Autowired
    private SpelRuleEngine spelRuleEngine;

    @Autowired
    private ItemService itemService;

    private InvoiceDomainObject testInvoice;

    @BeforeEach
    void setUp() {
        // 创建测试发票
        testInvoice = new InvoiceDomainObject();
        
        // 创建测试商品
        List<InvoiceItem> items = new ArrayList<>();
        
        InvoiceItem item1 = new InvoiceItem();
        item1.setDescription("苹果手机");
        item1.setName(null); // 待补全
        items.add(item1);
        
        InvoiceItem item2 = new InvoiceItem();
        item2.setDescription("华为笔记本");
        item2.setName(null); // 待补全
        items.add(item2);
        
        InvoiceItem item3 = new InvoiceItem();
        item3.setDescription("小米耳机");
        item3.setName(null); // 待补全
        items.add(item3);
        
        testInvoice.setItems(items);
    }

    @Test
    void testBatchProcessingRule() {
        // 创建批量处理规则
        BusinessRule batchRule = new BusinessRule();
        batchRule.setRuleId("completion_item_001");
        batchRule.setRuleName("批量补全商品标准名称");
        batchRule.setRuleType("completion");
        batchRule.setIsActive(true);
        batchRule.setPriority(100);
        batchRule.setApplyTo("invoice.items != null && invoice.items.size() > 0");
        batchRule.setTargetField("invoice.items.![name]");
        batchRule.setRuleExpression("@itemService.completeAllItemNames(invoice.items)");

        // 应用规则
        List<BusinessRule> rules = List.of(batchRule);
        Map<String, Object> result = spelRuleEngine.applyCompletionRules(testInvoice, rules);

        // 验证结果
        assertNotNull(result);
        assertTrue((Boolean) result.getOrDefault("success", false) || result.containsKey("rule_results"));
        
        // 验证商品名称是否被正确设置
        List<InvoiceItem> items = testInvoice.getItems();
        assertNotNull(items);
        assertEquals(3, items.size());
        
        // 验证每个商品的名称都被设置了
        for (InvoiceItem item : items) {
            assertNotNull(item.getName(), "商品名称应该被设置");
            assertFalse(item.getName().trim().isEmpty(), "商品名称不应该为空");
        }
        
        System.out.println("批量处理结果:");
        for (int i = 0; i < items.size(); i++) {
            InvoiceItem item = items.get(i);
            System.out.printf("商品 %d: %s -> %s (类型: %s)%n", 
                i + 1, 
                item.getDescription(), 
                item.getName(),
                item.getName() != null ? item.getName().getClass().getSimpleName() : "null");
            
            // 检查名称是否为字符串而不是列表
            assertTrue(item.getName() instanceof String, 
                "商品名称应该是字符串，不应该是列表");
        }
    }

    @Test
    void testShouldUseBatchProcessing() throws Exception {
        // 使用反射测试 shouldUseBatchProcessing 方法
        java.lang.reflect.Method method = SpelRuleEngine.class.getDeclaredMethod("shouldUseBatchProcessing", BusinessRule.class);
        method.setAccessible(true);

        // 测试批量处理规则
        BusinessRule batchRule = new BusinessRule();
        batchRule.setRuleExpression("@itemService.completeAllItemNames(invoice.items)");
        
        Boolean result = (Boolean) method.invoke(spelRuleEngine, batchRule);
        assertTrue(result, "包含 completeAllItemNames 的规则应该被识别为批量处理");

        // 测试非批量处理规则
        BusinessRule normalRule = new BusinessRule();
        normalRule.setRuleExpression("@itemService.getStandardNameByDescription(description)");
        
        Boolean result2 = (Boolean) method.invoke(spelRuleEngine, normalRule);
        assertFalse(result2, "不包含批量处理方法的规则不应该被识别为批量处理");
    }
}