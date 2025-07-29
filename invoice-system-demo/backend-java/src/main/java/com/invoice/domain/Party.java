package com.invoice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 参与方信息
 * 
 * 与 Python Party 模型功能完全等价
 * 表示供应商或客户的基本信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Party {

    /**
     * 公司名称
     */
    @JsonProperty("name")
    @NotBlank(message = "公司名称不能为空")
    private String name;

    /**
     * 标准化名称
     * 
     * 用于数据库查询和匹配
     */
    @JsonProperty("standard_name")
    private String standardName;

    /**
     * 税号
     */
    @JsonProperty("tax_no")
    private String taxNo;

    /**
     * 地址信息
     */
    @JsonProperty("address")
    @Valid
    private Address address;

    /**
     * 联系电话
     */
    @JsonProperty("phone")
    private String phone;

    /**
     * 电子邮箱
     */
    @JsonProperty("email")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 银行账户信息
     */
    @JsonProperty("bank_account")
    private String bankAccount;

    /**
     * 开户银行
     */
    @JsonProperty("bank_name")
    private String bankName;

    /**
     * 公司类型
     */
    @JsonProperty("company_type")
    private String companyType;

    /**
     * 法定代表人
     */
    @JsonProperty("legal_representative")
    private String legalRepresentative;

    /**
     * 注册资本
     */
    @JsonProperty("registered_capital")
    private String registeredCapital;

    /**
     * 企业规模
     */
    @JsonProperty("company_scale")
    private String companyScale;

    /**
     * 行业分类
     */
    @JsonProperty("industry_classification")
    private String industryClassification;

    /**
     * 企业状态
     */
    @JsonProperty("company_status")
    private String companyStatus;

    /**
     * 检查税号是否有效
     * 
     * @return 税号是否有效
     */
    public boolean hasTaxNo() {
        return taxNo != null && !taxNo.trim().isEmpty();
    }

    /**
     * 检查联系信息是否完整
     * 
     * @return 联系信息是否完整
     */
    public boolean hasCompleteContactInfo() {
        return (phone != null && !phone.trim().isEmpty()) ||
               (email != null && !email.trim().isEmpty());
    }

    /**
     * 检查银行信息是否完整
     * 
     * @return 银行信息是否完整
     */
    public boolean hasCompleteBankInfo() {
        return (bankAccount != null && !bankAccount.trim().isEmpty()) &&
               (bankName != null && !bankName.trim().isEmpty());
    }

    /**
     * 获取完整地址字符串
     * 
     * @return 完整地址
     */
    public String getFullAddress() {
        if (address == null) {
            return null;
        }
        return address.getFullAddress();
    }

    /**
     * 检查是否为大型企业
     * 
     * @return 是否为大型企业
     */
    public boolean isLargeCompany() {
        return "LARGE".equalsIgnoreCase(companyScale);
    }

    /**
     * 检查企业是否活跃
     * 
     * @return 企业是否活跃
     */
    public boolean isActive() {
        return !"INACTIVE".equalsIgnoreCase(companyStatus) &&
               !"CANCELLED".equalsIgnoreCase(companyStatus);
    }

    /**
     * 获取标准名称
     * 
     * @return 标准名称
     */
    public String getStandardName() {
        return standardName;
    }

    /**
     * 获取显示名称
     * 
     * 优先使用标准名称，若无则使用原始名称
     * 
     * @return 显示名称
     */
    public String getDisplayName() {
        return standardName != null && !standardName.trim().isEmpty() 
               ? standardName : name;
    }
}