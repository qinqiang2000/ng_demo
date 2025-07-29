package com.invoice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 税率配置实体
 * 
 * 与 Python TaxRate 模型功能完全等价
 * 用于存储不同类型的税率配置信息
 */
@Entity
@Table(name = "tax_rates", indexes = {
    @Index(name = "idx_tax_rate_category", columnList = "category"),
    @Index(name = "idx_tax_rate_amount", columnList = "min_amount, max_amount")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRate {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 税率名称
     */
    @Column(name = "name", nullable = false, length = 100)
    @NotBlank(message = "税率名称不能为空")
    @Size(max = 100, message = "税率名称不能超过100个字符")
    private String name;

    /**
     * 税率值
     * 
     * 以小数形式存储，例如：0.06 表示 6%
     */
    @Column(name = "rate", nullable = false, precision = 10, scale = 4)
    @NotNull(message = "税率值不能为空")
    @PositiveOrZero(message = "税率值不能为负数")
    private BigDecimal rate;

    /**
     * 适用类别
     * 
     * 可选值：GENERAL, TECH, TRAVEL_SERVICE, TRADING 等
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * 最小适用金额
     */
    @Column(name = "min_amount", precision = 15, scale = 2)
    @PositiveOrZero(message = "最小适用金额不能为负数")
    @Builder.Default
    private BigDecimal minAmount = BigDecimal.ZERO;

    /**
     * 最大适用金额
     */
    @Column(name = "max_amount", precision = 15, scale = 2)
    @PositiveOrZero(message = "最大适用金额不能为负数")
    private BigDecimal maxAmount;

    /**
     * 税率说明
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 是否启用
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 检查税率是否适用于指定金额
     * 
     * @param amount 金额
     * @return 是否适用
     */
    public boolean isApplicableToAmount(BigDecimal amount) {
        if (!isActive || amount == null) {
            return false;
        }

        // 检查最小金额限制
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            return false;
        }

        // 检查最大金额限制
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            return false;
        }

        return true;
    }

    /**
     * 检查税率是否适用于指定类别
     * 
     * @param targetCategory 目标类别
     * @return 是否适用
     */
    public boolean isApplicableToCategory(String targetCategory) {
        if (!isActive) {
            return false;
        }

        // 如果没有指定类别，则适用于所有类别
        if (category == null || category.trim().isEmpty()) {
            return true;
        }

        // 检查类别匹配
        return category.equalsIgnoreCase(targetCategory);
    }

    /**
     * 计算税额
     * 
     * @param amount 计税基数
     * @return 税额
     */
    public BigDecimal calculateTaxAmount(BigDecimal amount) {
        if (amount == null || rate == null || !isActive) {
            return BigDecimal.ZERO;
        }

        if (!isApplicableToAmount(amount)) {
            return BigDecimal.ZERO;
        }

        return amount.multiply(rate).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 获取税率百分比
     * 
     * @return 税率百分比字符串，例如："6.00%"
     */
    public String getRatePercentage() {
        if (rate == null) {
            return "0.00%";
        }
        
        BigDecimal percentage = rate.multiply(new BigDecimal("100"));
        return String.format("%.2f%%", percentage);
    }

    /**
     * 检查是否为零税率
     * 
     * @return 是否为零税率
     */
    public boolean isZeroRate() {
        return rate != null && rate.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 检查是否为标准税率
     * 
     * 标准增值税税率通常为 6% 或 13%
     * 
     * @return 是否为标准税率
     */
    public boolean isStandardRate() {
        if (rate == null) {
            return false;
        }
        
        BigDecimal rate6 = new BigDecimal("0.06");
        BigDecimal rate13 = new BigDecimal("0.13");
        
        return rate.compareTo(rate6) == 0 || rate.compareTo(rate13) == 0;
    }

    /**
     * 检查是否为小规模纳税人税率
     * 
     * 小规模纳税人税率通常为 3%
     * 
     * @return 是否为小规模纳税人税率
     */
    public boolean isSmallScaleRate() {
        if (rate == null) {
            return false;
        }
        
        BigDecimal rate3 = new BigDecimal("0.03");
        return rate.compareTo(rate3) == 0;
    }

    /**
     * 获取适用范围描述
     * 
     * @return 适用范围描述
     */
    public String getApplicabilityDescription() {
        StringBuilder desc = new StringBuilder();
        
        if (category != null && !category.trim().isEmpty()) {
            desc.append("适用于").append(getCategoryDescription());
        } else {
            desc.append("适用于所有类别");
        }
        
        if (minAmount != null && minAmount.compareTo(BigDecimal.ZERO) > 0) {
            desc.append("，金额≥").append(minAmount);
        }
        
        if (maxAmount != null) {
            desc.append("，金额≤").append(maxAmount);
        }
        
        return desc.toString();
    }

    /**
     * 获取类别描述
     * 
     * @return 类别描述
     */
    private String getCategoryDescription() {
        if (category == null) {
            return "所有企业";
        }
        
        switch (category.toUpperCase()) {
            case "GENERAL":
                return "一般企业";
            case "TECH":
                return "科技企业";
            case "TRAVEL_SERVICE":
                return "旅游服务企业";
            case "TRADING":
                return "贸易企业";
            default:
                return category;
        }
    }
}