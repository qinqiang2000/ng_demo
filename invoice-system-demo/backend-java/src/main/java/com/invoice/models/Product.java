package com.invoice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品信息实体
 * 
 * 对应数据库中的 products 表
 * 支持产品基本信息、定价和分类管理
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_name", columnList = "name"),
    @Index(name = "idx_product_category", columnList = "category"),
    @Index(name = "idx_product_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 产品名称
     */
    @Column(name = "name", nullable = false, length = 200)
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 200, message = "产品名称长度不能超过200字符")
    private String name;

    /**
     * 产品描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    @Size(max = 1000, message = "产品描述长度不能超过1000字符")
    private String description;

    /**
     * 产品分类
     */
    @Column(name = "category", length = 100)
    @Size(max = 100, message = "产品分类长度不能超过100字符")
    private String category;

    /**
     * 单价
     */
    @Column(name = "unit_price", precision = 15, scale = 2)
    @PositiveOrZero(message = "单价不能为负数")
    private BigDecimal unitPrice;

    /**
     * 单位
     */
    @Column(name = "unit", length = 20)
    @Size(max = 20, message = "单位长度不能超过20字符")
    private String unit;

    /**
     * 税率 (百分比，如 13.00 表示 13%)
     */
    @Column(name = "tax_rate", precision = 5, scale = 2)
    @DecimalMin(value = "0.00", message = "税率不能为负数")
    @DecimalMax(value = "100.00", message = "税率不能超过100%")
    private BigDecimal taxRate;

    /**
     * 产品编码
     */
    @Column(name = "product_code", length = 50)
    @Size(max = 50, message = "产品编码长度不能超过50字符")
    private String productCode;

    /**
     * 规格型号
     */
    @Column(name = "specification", length = 200)
    @Size(max = 200, message = "规格型号长度不能超过200字符")
    private String specification;

    /**
     * 品牌
     */
    @Column(name = "brand", length = 100)
    @Size(max = 100, message = "品牌长度不能超过100字符")
    private String brand;

    /**
     * 状态 (active/inactive/discontinued)
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "active";

    /**
     * 是否活跃
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 备注
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    @Size(max = 500, message = "备注长度不能超过500字符")
    private String notes;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 更新时间自动设置
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 获取显示名称
     * 
     * 如果有规格型号，则显示"产品名称 (规格型号)"
     * 否则只显示产品名称
     */
    public String getDisplayName() {
        if (specification != null && !specification.trim().isEmpty()) {
            return name + " (" + specification + ")";
        }
        return name;
    }

    /**
     * 获取完整描述
     * 
     * 包含名称、规格、品牌等信息
     */
    public String getFullDescription() {
        StringBuilder sb = new StringBuilder(name);
        
        if (specification != null && !specification.trim().isEmpty()) {
            sb.append(" ").append(specification);
        }
        
        if (brand != null && !brand.trim().isEmpty()) {
            sb.append(" [").append(brand).append("]");
        }
        
        return sb.toString();
    }

    /**
     * 判断产品是否可用
     */
    public boolean isAvailable() {
        return isActive != null && isActive && 
               ("active".equalsIgnoreCase(status) || status == null);
    }

    /**
     * 计算含税单价
     */
    public BigDecimal getTaxInclusivePrice() {
        if (unitPrice == null) {
            return null;
        }
        
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) == 0) {
            return unitPrice;
        }
        
        BigDecimal taxMultiplier = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100")));
        return unitPrice.multiply(taxMultiplier).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 计算税额
     */
    public BigDecimal getTaxAmount() {
        if (unitPrice == null || taxRate == null) {
            return BigDecimal.ZERO;
        }
        
        return unitPrice.multiply(taxRate.divide(new BigDecimal("100")))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
    }
}