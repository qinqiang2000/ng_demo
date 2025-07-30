package com.invoice.spel.services;

import com.invoice.domain.InvoiceItem;
import com.invoice.models.Product;
import com.invoice.models.TaxRate;
import com.invoice.repository.ProductRepository;
import com.invoice.repository.TaxRateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SpEL 商品服务
 * 
 * 提供批量商品处理功能，支持：
 * - 批量补全商品税率
 * - 批量补全商品税收分类
 * - 批量计算商品税额
 * - 批量验证商品税率
 */
@Service
@Slf4j
public class ItemService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private TaxRateRepository taxRateRepository;
    
    /**
     * 批量补全商品税率
     * 
     * @param items 商品明细列表
     * @return 成功处理的商品数量
     */
    public int completeItemTaxRates(List<InvoiceItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        
        log.debug("开始批量补全商品税率，商品数量: {}", items.size());
        
        int successCount = 0;
        for (InvoiceItem item : items) {
            try {
                if (item.getTaxRate() == null && item.getName() != null) {
                    // 根据商品名称查找产品信息
                    Optional<Product> product = productRepository.findByName(item.getName().trim());
                    if (product.isPresent() && product.get().getTaxRate() != null) {
                        // 将百分比税率转换为小数形式
                        BigDecimal taxRate = product.get().getTaxRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                        item.setTaxRate(taxRate);
                        successCount++;
                        log.debug("补全商品 {} 的税率: {}", item.getName(), taxRate);
                    }
                }
            } catch (Exception e) {
                log.warn("补全商品 {} 税率时发生异常: {}", item.getName(), e.getMessage());
            }
        }
        
        log.debug("批量补全商品税率完成，成功处理 {} 个商品", successCount);
        return successCount;
    }
    
    /**
     * 批量补全商品税收分类
     * 
     * @param items 商品明细列表
     * @return 成功处理的商品数量
     */
    public int completeItemTaxCategories(List<InvoiceItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        
        log.debug("开始批量补全商品税收分类，商品数量: {}", items.size());
        
        int successCount = 0;
        for (InvoiceItem item : items) {
            try {
                if (item.getTaxCategory() == null && item.getName() != null) {
                    // 根据商品名称查找产品信息
                    Optional<Product> product = productRepository.findByName(item.getName().trim());
                    if (product.isPresent() && product.get().getCategory() != null) {
                        item.setTaxCategory(product.get().getCategory());
                        successCount++;
                        log.debug("补全商品 {} 的税收分类: {}", item.getName(), product.get().getCategory());
                    }
                }
            } catch (Exception e) {
                log.warn("补全商品 {} 税收分类时发生异常: {}", item.getName(), e.getMessage());
            }
        }
        
        log.debug("批量补全商品税收分类完成，成功处理 {} 个商品", successCount);
        return successCount;
    }
    
    /**
     * 批量计算商品税额
     * 
     * @param items 商品明细列表
     * @return 成功处理的商品数量
     */
    public int calculateItemTaxAmounts(List<InvoiceItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        
        log.debug("开始批量计算商品税额，商品数量: {}", items.size());
        
        int successCount = 0;
        for (InvoiceItem item : items) {
            try {
                if (item.getTaxAmount() == null && 
                    item.getAmount() != null && 
                    item.getTaxRate() != null) {
                    
                    // 计算税额 = 金额 × 税率
                    BigDecimal taxAmount = item.getAmount()
                        .multiply(item.getTaxRate())
                        .setScale(2, RoundingMode.HALF_UP);
                    
                    item.setTaxAmount(taxAmount);
                    successCount++;
                    log.debug("计算商品 {} 的税额: {}", item.getName(), taxAmount);
                }
            } catch (Exception e) {
                log.warn("计算商品 {} 税额时发生异常: {}", item.getName(), e.getMessage());
            }
        }
        
        log.debug("批量计算商品税额完成，成功处理 {} 个商品", successCount);
        return successCount;
    }
    
    /**
     * 验证批量订单税率
     * 
     * @param items 商品明细列表
     * @return 验证结果统计
     */
    public Map<String, Object> validateBulkOrderTaxRates(List<InvoiceItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of("valid", true, "message", "无商品明细");
        }
        
        log.debug("开始验证批量订单税率，商品数量: {}", items.size());
        
        // 统计不同税率的商品数量
        Map<BigDecimal, Long> taxRateStats = items.stream()
            .filter(item -> item.getTaxRate() != null)
            .collect(Collectors.groupingBy(
                InvoiceItem::getTaxRate,
                Collectors.counting()
            ));
        
        // 检查是否所有商品都有相同的税率
        boolean allSameTaxRate = taxRateStats.size() <= 1;
        
        // 检查是否有无效税率
        boolean hasInvalidTaxRate = items.stream()
            .anyMatch(item -> item.getTaxRate() != null && 
                     (item.getTaxRate().compareTo(BigDecimal.ZERO) < 0 || 
                      item.getTaxRate().compareTo(new BigDecimal("1.0")) > 0));
        
        Map<String, Object> result = Map.of(
            "valid", allSameTaxRate && !hasInvalidTaxRate,
            "allSameTaxRate", allSameTaxRate,
            "hasInvalidTaxRate", hasInvalidTaxRate,
            "taxRateStats", taxRateStats,
            "message", allSameTaxRate ? "税率一致" : "存在不同税率的商品"
        );
        
        log.debug("批量订单税率验证完成: {}", result);
        return result;
    }
    
    /**
     * 获取商品标准名称
     * 
     * @param itemName 商品名称
     * @return 标准名称
     */
    public String getStandardName(String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return null;
        }
        
        Optional<Product> product = productRepository.findByName(itemName.trim());
        return product.map(Product::getName).orElse(itemName);
    }
    
    /**
     * 获取商品税收分类
     * 
     * @param itemName 商品名称
     * @return 税收分类
     */
    public String getTaxCategory(String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return null;
        }
        
        Optional<Product> product = productRepository.findByName(itemName.trim());
        return product.map(Product::getCategory).orElse(null);
    }
    
    /**
     * 获取商品推荐税率
     * 
     * @param itemName 商品名称
     * @return 推荐税率（小数形式）
     */
    public BigDecimal getRecommendedTaxRate(String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            return null;
        }
        
        Optional<Product> product = productRepository.findByName(itemName.trim());
        if (product.isPresent() && product.get().getTaxRate() != null) {
            // 将百分比税率转换为小数形式
            return product.get().getTaxRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }
        
        return null;
    }
}