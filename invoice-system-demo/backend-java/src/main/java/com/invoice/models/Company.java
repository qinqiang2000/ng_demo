package com.invoice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 企业信息实体
 * 
 * 与 Python Company 模型功能完全等价
 * 用于存储和查询企业基础信息
 */
@Entity
@Table(name = "companies", indexes = {
    @Index(name = "idx_company_name", columnList = "name"),
    @Index(name = "idx_company_category", columnList = "category"),
    @Index(name = "idx_company_tax_number", columnList = "tax_number")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 企业名称
     */
    @Column(name = "name", nullable = false, length = 200)
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 200, message = "企业名称不能超过200个字符")
    private String name;

    /**
     * 税号（统一社会信用代码）
     */
    @Column(name = "tax_number", unique = true, length = 20)
    @Size(max = 20, message = "税号不能超过20个字符")
    private String taxNumber;

    /**
     * 企业地址
     */
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    /**
     * 联系电话
     */
    @Column(name = "phone", length = 50)
    @Size(max = 50, message = "联系电话不能超过50个字符")
    private String phone;

    /**
     * 邮箱地址
     */
    @Column(name = "email", length = 100)
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    /**
     * 企业分类
     * 
     * 可选值：GENERAL, TECH, TRAVEL_SERVICE, TRADING 等
     */
    @Column(name = "category", length = 50)
    @Builder.Default
    private String category = "GENERAL";

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
     * 检查企业是否有效
     * 
     * @return 企业是否有效
     */
    public boolean isValid() {
        return isActive != null && isActive && 
               name != null && !name.trim().isEmpty();
    }

    /**
     * 检查是否有税号
     * 
     * @return 是否有税号
     */
    public boolean hasTaxNumber() {
        return taxNumber != null && !taxNumber.trim().isEmpty();
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
     * 检查是否为科技企业
     * 
     * @return 是否为科技企业
     */
    public boolean isTechCompany() {
        return "TECH".equalsIgnoreCase(category);
    }

    /**
     * 检查是否为旅游服务企业
     * 
     * @return 是否为旅游服务企业
     */
    public boolean isTravelServiceCompany() {
        return "TRAVEL_SERVICE".equalsIgnoreCase(category);
    }

    /**
     * 检查是否为贸易企业
     * 
     * @return 是否为贸易企业
     */
    public boolean isTradingCompany() {
        return "TRADING".equalsIgnoreCase(category);
    }

    /**
     * 获取企业类型描述
     * 
     * @return 企业类型描述
     */
    public String getCategoryDescription() {
        if (category == null) {
            return "一般企业";
        }
        
        switch (category.toUpperCase()) {
            case "TECH":
                return "科技企业";
            case "TRAVEL_SERVICE":
                return "旅游服务";
            case "TRADING":
                return "贸易公司";
            case "GENERAL":
            default:
                return "一般企业";
        }
    }

    /**
     * 获取显示名称
     * 
     * @return 显示名称
     */
    public String getDisplayName() {
        return name;
    }
}