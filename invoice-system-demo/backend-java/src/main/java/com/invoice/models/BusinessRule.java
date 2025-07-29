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
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 业务规则配置实体
 * 
 * 与 Python BusinessRule 模型功能完全等价
 * 用于存储动态业务规则配置（可选，主要规则通过 rules.yaml 配置）
 */
@Entity
@Table(name = "business_rules", indexes = {
    @Index(name = "idx_rule_type", columnList = "rule_type"),
    @Index(name = "idx_rule_priority", columnList = "priority"),
    @Index(name = "idx_rule_id", columnList = "rule_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessRule {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 规则ID（唯一标识）
     */
    @Column(name = "rule_id", unique = true, nullable = false, length = 50)
    @NotBlank(message = "规则ID不能为空")
    @Size(max = 50, message = "规则ID不能超过50个字符")
    private String ruleId;

    /**
     * 规则名称
     */
    @Column(name = "rule_name", nullable = false, length = 200)
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称不能超过200个字符")
    private String ruleName;

    /**
     * 规则类型
     * 
     * 可选值：completion（字段补全）, validation（业务校验）
     */
    @Column(name = "rule_type", nullable = false, length = 20)
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /**
     * 应用条件
     * 
     * CEL 表达式，用于判断规则何时应用
     */
    @Column(name = "apply_to", columnDefinition = "TEXT")
    private String applyTo;

    /**
     * 目标字段
     * 
     * 用于字段补全规则，指定要填充的字段路径
     */
    @Column(name = "target_field", length = 100)
    @Size(max = 100, message = "目标字段不能超过100个字符")
    private String targetField;

    /**
     * 校验字段路径
     * 
     * 用于业务校验规则，指定要校验的字段路径
     */
    @Column(name = "field_path", length = 100)
    @Size(max = 100, message = "校验字段路径不能超过100个字符")
    private String fieldPath;

    /**
     * 规则表达式
     * 
     * CEL 表达式，定义具体的业务逻辑
     */
    @Column(name = "rule_expression", nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "规则表达式不能为空")
    private String ruleExpression;

    /**
     * 错误消息
     * 
     * 用于业务校验规则，定义校验失败时的错误提示
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 优先级
     * 
     * 数值越大优先级越高，默认为 50
     */
    @Column(name = "priority")
    @NotNull
    @Builder.Default
    private Integer priority = 50;

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
     * 检查是否为字段补全规则
     * 
     * @return 是否为字段补全规则
     */
    public boolean isCompletionRule() {
        return "completion".equalsIgnoreCase(ruleType);
    }

    /**
     * 检查是否为业务校验规则
     * 
     * @return 是否为业务校验规则
     */
    public boolean isValidationRule() {
        return "validation".equalsIgnoreCase(ruleType);
    }

    /**
     * 检查规则是否有效
     * 
     * @return 规则是否有效
     */
    public boolean isValid() {
        if (!isActive || ruleExpression == null || ruleExpression.trim().isEmpty()) {
            return false;
        }

        if (isCompletionRule()) {
            return targetField != null && !targetField.trim().isEmpty();
        } else if (isValidationRule()) {
            return fieldPath != null && !fieldPath.trim().isEmpty() &&
                   errorMessage != null && !errorMessage.trim().isEmpty();
        }

        return false;
    }

    /**
     * 检查是否有应用条件
     * 
     * @return 是否有应用条件
     */
    public boolean hasApplyCondition() {
        return applyTo != null && !applyTo.trim().isEmpty();
    }

    /**
     * 获取规则类型描述
     * 
     * @return 规则类型描述
     */
    public String getRuleTypeDescription() {
        if (ruleType == null) {
            return "未知类型";
        }
        
        switch (ruleType.toLowerCase()) {
            case "completion":
                return "字段补全";
            case "validation":
                return "业务校验";
            default:
                return ruleType;
        }
    }

    /**
     * 获取优先级描述
     * 
     * @return 优先级描述
     */
    public String getPriorityDescription() {
        if (priority == null) {
            return "普通";
        }
        
        if (priority >= 90) {
            return "最高";
        } else if (priority >= 70) {
            return "高";
        } else if (priority >= 30) {
            return "普通";
        } else {
            return "低";
        }
    }

    /**
     * 获取状态描述
     * 
     * @return 状态描述
     */
    public String getStatusDescription() {
        if (isActive == null || !isActive) {
            return "已禁用";
        }
        
        if (isValid()) {
            return "正常";
        } else {
            return "配置错误";
        }
    }

    /**
     * 获取规则摘要
     * 
     * @return 规则摘要
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(getRuleTypeDescription()).append("规则：").append(ruleName);
        
        if (isCompletionRule() && targetField != null) {
            summary.append("，目标字段：").append(targetField);
        } else if (isValidationRule() && fieldPath != null) {
            summary.append("，校验字段：").append(fieldPath);
        }
        
        summary.append("，优先级：").append(getPriorityDescription());
        summary.append("，状态：").append(getStatusDescription());
        
        return summary.toString();
    }
}