package com.invoice.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发票领域对象
 * 
 * 与 Python InvoiceDomainObject 功能完全等价
 * 包含发票的所有业务信息，支持 CEL 表达式访问
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDomainObject {

    /**
     * 发票号码
     */
    @JsonProperty("invoice_number")
    @NotBlank(message = "发票号码不能为空")
    private String invoiceNumber;

    /**
     * 开票日期
     */
    @JsonProperty("issue_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "开票日期不能为空")
    private LocalDate issueDate;

    /**
     * 发票总金额
     */
    @JsonProperty("total_amount")
    @NotNull(message = "发票金额不能为空")
    @PositiveOrZero(message = "发票金额不能为负数")
    private BigDecimal totalAmount;

    /**
     * 税额
     */
    @JsonProperty("tax_amount")
    @PositiveOrZero(message = "税额不能为负数")
    private BigDecimal taxAmount;

    /**
     * 币种代码
     */
    @JsonProperty("currency")
    @Builder.Default
    private String currency = "CNY";

    /**
     * 国家代码
     */
    @JsonProperty("country")
    private String country;

    /**
     * 扩展字段
     */
    @JsonProperty("extensions")
    @Builder.Default
    private Map<String, Object> extensions = new HashMap<>();

    /**
     * 供应商信息
     */
    @JsonProperty("supplier")
    @Valid
    @NotNull(message = "供应商信息不能为空")
    private Party supplier;

    /**
     * 客户信息
     */
    @JsonProperty("customer")
    @Valid
    @NotNull(message = "客户信息不能为空")
    private Party customer;

    /**
     * 发票明细项
     */
    @JsonProperty("items")
    @Valid
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    /**
     * 付款条款
     */
    @JsonProperty("payment_terms")
    private String paymentTerms;

    /**
     * 备注信息
     */
    @JsonProperty("notes")
    private String notes;

    /**
     * 发票类型
     */
    @JsonProperty("invoice_type")
    private String invoiceType;

    /**
     * 发票状态
     */
    @JsonProperty("status")
    @Builder.Default
    private String status = "DRAFT";

    /**
     * 到期日期
     */
    @JsonProperty("due_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    /**
     * 参考号码
     */
    @JsonProperty("reference_number")
    private String referenceNumber;

    /**
     * 税率
     */
    @JsonProperty("tax_rate")
    @PositiveOrZero(message = "税率不能为负数")
    private BigDecimal taxRate;

    /**
     * 不含税金额
     */
    @JsonProperty("net_amount")
    @PositiveOrZero(message = "不含税金额不能为负数")
    private BigDecimal netAmount;

    /**
     * 发票类别
     */
    @JsonProperty("invoice_category")
    private String invoiceCategory;

    /**
     * 行业类别
     */
    @JsonProperty("industry_category")
    private String industryCategory;

    /**
     * 项目名称
     */
    @JsonProperty("project_name")
    private String projectName;

    /**
     * 合同编号
     */
    @JsonProperty("contract_number")
    private String contractNumber;

    /**
     * 审批状态
     */
    @JsonProperty("approval_status")
    private String approvalStatus;

    /**
     * 创建时间戳
     */
    @JsonProperty("created_at")
    private String createdAt;

    /**
     * 更新时间戳
     */
    @JsonProperty("updated_at")
    private String updatedAt;

    /**
     * 计算不含税金额
     * 
     * @return 不含税金额
     */
    public BigDecimal calculateNetAmount() {
        if (totalAmount == null || taxAmount == null) {
            return totalAmount;
        }
        return totalAmount.subtract(taxAmount);
    }

    /**
     * 计算税率
     * 
     * @return 税率（百分比）
     */
    public BigDecimal calculateTaxRate() {
        if (totalAmount == null || taxAmount == null || 
            totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal net = calculateNetAmount();
        if (net.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return taxAmount.divide(net, 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 获取发票项总数
     * 
     * @return 发票项数量
     */
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    /**
     * 检查是否为大额发票
     * 
     * @param threshold 大额阈值
     * @return 是否为大额发票
     */
    public boolean isLargeAmount(BigDecimal threshold) {
        return totalAmount != null && totalAmount.compareTo(threshold) > 0;
    }

    /**
     * 获取供应商标准名称
     * 
     * @return 供应商标准名称
     */
    public String getSupplierStandardName() {
        return supplier != null ? supplier.getStandardName() : null;
    }

    /**
     * 获取客户标准名称
     * 
     * @return 客户标准名称
     */
    public String getCustomerStandardName() {
        return customer != null ? customer.getStandardName() : null;
    }
}