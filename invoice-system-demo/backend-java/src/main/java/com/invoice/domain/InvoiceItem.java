package com.invoice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 发票明细项
 * 
 * 与 Python InvoiceItem 模型功能完全等价
 * 表示发票中的单个商品或服务项目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItem {

    /**
     * 商品或服务名称
     */
    @JsonProperty("name")
    @NotBlank(message = "商品名称不能为空")
    private String name;

    /**
     * 商品描述
     */
    @JsonProperty("description")
    private String description;

    /**
     * 数量
     */
    @JsonProperty("quantity")
    @NotNull(message = "数量不能为空")
    @PositiveOrZero(message = "数量不能为负数")
    private BigDecimal quantity;

    /**
     * 单价
     */
    @JsonProperty("unit_price")
    @NotNull(message = "单价不能为空")
    @PositiveOrZero(message = "单价不能为负数")
    private BigDecimal unitPrice;

    /**
     * 行总额
     */
    @JsonProperty("line_total")
    @PositiveOrZero(message = "行总额不能为负数")
    private BigDecimal lineTotal;

    /**
     * 税率
     */
    @JsonProperty("tax_rate")
    @PositiveOrZero(message = "税率不能为负数")
    private BigDecimal taxRate;

    /**
     * 税额
     */
    @JsonProperty("tax_amount")
    @PositiveOrZero(message = "税额不能为负数")
    private BigDecimal taxAmount;

    /**
     * 税种
     */
    @JsonProperty("tax_category")
    private String taxCategory;

    /**
     * 金额
     */
    @JsonProperty("amount")
    @PositiveOrZero(message = "金额不能为负数")
    private BigDecimal amount;

    /**
     * 单位
     */
    @JsonProperty("unit")
    private String unit;

    /**
     * 商品编码
     */
    @JsonProperty("product_code")
    private String productCode;

    /**
     * 商品分类
     */
    @JsonProperty("category")
    private String category;

    /**
     * 折扣率
     */
    @JsonProperty("discount_rate")
    @PositiveOrZero(message = "折扣率不能为负数")
    private BigDecimal discountRate;

    /**
     * 折扣金额
     */
    @JsonProperty("discount_amount")
    @PositiveOrZero(message = "折扣金额不能为负数")
    private BigDecimal discountAmount;

    /**
     * 净金额（扣除折扣后）
     */
    @JsonProperty("net_amount")
    @PositiveOrZero(message = "净金额不能为负数")
    private BigDecimal netAmount;

    /**
     * 商品规格
     */
    @JsonProperty("specification")
    private String specification;

    /**
     * 品牌
     */
    @JsonProperty("brand")
    private String brand;

    /**
     * 型号
     */
    @JsonProperty("model")
    private String model;

    /**
     * 备注
     */
    @JsonProperty("remarks")
    private String remarks;

    /**
     * 计算行总额
     * 
     * 根据数量和单价计算行总额
     * 
     * @return 行总额
     */
    public BigDecimal calculateLineTotal() {
        if (quantity == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(unitPrice);
    }

    /**
     * 计算折扣后金额
     * 
     * @return 折扣后金额
     */
    public BigDecimal calculateDiscountedAmount() {
        BigDecimal total = calculateLineTotal();
        if (discountAmount != null) {
            return total.subtract(discountAmount);
        } else if (discountRate != null) {
            BigDecimal discount = total.multiply(discountRate);
            return total.subtract(discount);
        }
        return total;
    }

    /**
     * 计算税额
     * 
     * @return 税额
     */
    public BigDecimal calculateTaxAmount() {
        if (taxRate == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = netAmount != null ? netAmount : calculateDiscountedAmount();
        return amount.multiply(taxRate);
    }

    /**
     * 计算含税总额
     * 
     * @return 含税总额
     */
    public BigDecimal calculateTotalWithTax() {
        BigDecimal amount = netAmount != null ? netAmount : calculateDiscountedAmount();
        BigDecimal tax = calculateTaxAmount();
        return amount.add(tax);
    }

    /**
     * 检查是否有折扣
     * 
     * @return 是否有折扣
     */
    public boolean hasDiscount() {
        return (discountRate != null && discountRate.compareTo(BigDecimal.ZERO) > 0) ||
               (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * 检查是否需要缴税
     * 
     * @return 是否需要缴税
     */
    public boolean isTaxable() {
        return taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 获取有效税率
     * 
     * @return 有效税率
     */
    public BigDecimal getEffectiveTaxRate() {
        return taxRate != null ? taxRate : BigDecimal.ZERO;
    }

    /**
     * 检查商品信息是否完整
     * 
     * @return 商品信息是否完整
     */
    public boolean isComplete() {
        return name != null && !name.trim().isEmpty() &&
               quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0 &&
               unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * 获取商品完整名称
     * 
     * 包含品牌、型号和规格信息
     * 
     * @return 完整名称
     */
    public String getFullName() {
        StringBuilder fullName = new StringBuilder(name);
        
        if (brand != null && !brand.trim().isEmpty()) {
            fullName.append(" (").append(brand);
            if (model != null && !model.trim().isEmpty()) {
                fullName.append(" ").append(model);
            }
            fullName.append(")");
        } else if (model != null && !model.trim().isEmpty()) {
            fullName.append(" (").append(model).append(")");
        }
        
        if (specification != null && !specification.trim().isEmpty()) {
            fullName.append(" - ").append(specification);
        }
        
        return fullName.toString();
    }
}