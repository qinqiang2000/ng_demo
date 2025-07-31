package com.invoice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 规则请求 DTO
 * 
 * 用于接收创建和更新规则的请求数据
 * 与 Python 版本的请求模型功能完全等价
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleRequest {

    /**
     * 规则ID（更新时必需，创建时可选）
     */
    private String ruleId;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    /**
     * 规则类型（completion/validation）
     */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /**
     * 应用条件（可选）
     */
    private String applyTo;

    /**
     * 目标字段（补全规则必需）
     */
    private String targetField;

    /**
     * 校验字段路径（校验规则必需）
     */
    private String fieldPath;

    /**
     * 规则表达式
     */
    @NotBlank(message = "规则表达式不能为空")
    private String ruleExpression;

    /**
     * 错误消息（校验规则必需）
     */
    private String errorMessage;

    /**
     * 优先级（1-100）
     */
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小值为1")
    @Max(value = 100, message = "优先级最大值为100")
    private Integer priority;

    /**
     * 是否激活
     */
    @NotNull(message = "激活状态不能为空")
    private Boolean active;

    /**
     * 规则描述（可选）
     */
    private String description;
}