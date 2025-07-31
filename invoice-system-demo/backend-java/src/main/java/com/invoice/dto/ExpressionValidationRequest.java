package com.invoice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 表达式验证请求 DTO
 * 
 * 用于验证规则表达式语法的请求数据
 * 与 Python 版本的表达式验证功能完全等价
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpressionValidationRequest {

    /**
     * 要验证的表达式
     */
    @NotBlank(message = "表达式不能为空")
    private String expression;

    /**
     * 表达式类型（可选，用于特定验证）
     */
    private String expressionType;

    /**
     * 规则类型（completion/validation）
     */
    private String ruleType;
}