package com.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoice.domain.InvoiceDomainObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 发票处理响应 DTO
 * 
 * 与 Python FastAPI 的响应模型完全等价
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInvoiceResponse {

    /**
     * 处理状态
     * 
     * 可选值：success, error, warning
     */
    @JsonProperty("status")
    private String status;

    /**
     * 响应消息
     */
    @JsonProperty("message")
    private String message;

    /**
     * 处理后的发票数据
     */
    @JsonProperty("invoice")
    private InvoiceDomainObject invoice;

    /**
     * 业务校验结果
     */
    @JsonProperty("validation")
    private ValidationResult validation;

    /**
     * 处理步骤信息
     */
    @JsonProperty("processing_steps")
    private List<ProcessingStep> processingSteps;

    /**
     * 处理统计信息
     */
    @JsonProperty("stats")
    private ProcessingStats stats;

    /**
     * 额外的元数据
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * 校验结果内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {

        /**
         * 校验是否通过
         */
        @JsonProperty("is_valid")
        private Boolean isValid;

        /**
         * 错误列表
         */
        @JsonProperty("errors")
        private List<ValidationError> errors;

        /**
         * 警告列表
         */
        @JsonProperty("warnings")
        private List<ValidationWarning> warnings;

        /**
         * 校验摘要
         */
        @JsonProperty("summary")
        private String summary;
    }

    /**
     * 校验错误内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {

        /**
         * 错误代码
         */
        @JsonProperty("code")
        private String code;

        /**
         * 错误消息
         */
        @JsonProperty("message")
        private String message;

        /**
         * 错误字段路径
         */
        @JsonProperty("field_path")
        private String fieldPath;

        /**
         * 错误严重级别
         */
        @JsonProperty("severity")
        private String severity;
    }

    /**
     * 校验警告内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {

        /**
         * 警告代码
         */
        @JsonProperty("code")
        private String code;

        /**
         * 警告消息
         */
        @JsonProperty("message")
        private String message;

        /**
         * 警告字段路径
         */
        @JsonProperty("field_path")
        private String fieldPath;
    }

    /**
     * 处理步骤内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStep {

        /**
         * 步骤名称
         */
        @JsonProperty("step_name")
        private String stepName;

        /**
         * 步骤状态
         */
        @JsonProperty("status")
        private String status;

        /**
         * 步骤描述
         */
        @JsonProperty("description")
        private String description;

        /**
         * 执行时间（毫秒）
         */
        @JsonProperty("duration_ms")
        private Long durationMs;

        /**
         * 步骤详情
         */
        @JsonProperty("details")
        private Map<String, Object> details;
    }

    /**
     * 处理统计信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStats {

        /**
         * 总处理时间（毫秒）
         */
        @JsonProperty("total_duration_ms")
        private Long totalDurationMs;

        /**
         * 字段补全数量
         */
        @JsonProperty("fields_completed")
        private Integer fieldsCompleted;

        /**
         * 校验规则数量
         */
        @JsonProperty("validation_rules_applied")
        private Integer validationRulesApplied;

        /**
         * 处理的明细项数量
         */
        @JsonProperty("items_processed")
        private Integer itemsProcessed;

        /**
         * 数据库查询次数
         */
        @JsonProperty("database_queries")
        private Integer databaseQueries;

        /**
         * 处理开始时间
         */
        @JsonProperty("start_time")
        private LocalDateTime startTime;

        /**
         * 处理结束时间
         */
        @JsonProperty("end_time")
        private LocalDateTime endTime;
    }

    /**
     * 检查处理是否成功
     * 
     * @return 是否成功
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

    /**
     * 检查是否有错误
     * 
     * @return 是否有错误
     */
    public boolean hasErrors() {
        return validation != null && 
               validation.getErrors() != null && 
               !validation.getErrors().isEmpty();
    }

    /**
     * 检查是否有警告
     * 
     * @return 是否有警告
     */
    public boolean hasWarnings() {
        return validation != null && 
               validation.getWarnings() != null && 
               !validation.getWarnings().isEmpty();
    }

    /**
     * 获取错误数量
     * 
     * @return 错误数量
     */
    public int getErrorCount() {
        return hasErrors() ? validation.getErrors().size() : 0;
    }

    /**
     * 获取警告数量
     * 
     * @return 警告数量
     */
    public int getWarningCount() {
        return hasWarnings() ? validation.getWarnings().size() : 0;
    }

    /**
     * 检查校验是否通过
     * 
     * @return 校验是否通过
     */
    public boolean isValidationPassed() {
        return validation != null && 
               validation.getIsValid() != null && 
               validation.getIsValid();
    }

    /**
     * 获取处理步骤数量
     * 
     * @return 处理步骤数量
     */
    public int getProcessingStepsCount() {
        return processingSteps != null ? processingSteps.size() : 0;
    }

    /**
     * 获取总处理时间
     * 
     * @return 总处理时间（毫秒）
     */
    public Long getTotalDurationMs() {
        return stats != null ? stats.getTotalDurationMs() : null;
    }

    /**
     * 获取字段补全数量
     * 
     * @return 字段补全数量
     */
    public Integer getFieldsCompleted() {
        return stats != null ? stats.getFieldsCompleted() : 0;
    }

    /**
     * 创建成功响应
     * 
     * @param invoice 处理后的发票
     * @param validation 校验结果
     * @return 成功响应
     */
    public static ProcessInvoiceResponse success(InvoiceDomainObject invoice, ValidationResult validation) {
        return ProcessInvoiceResponse.builder()
                .status("success")
                .message("发票处理成功")
                .invoice(invoice)
                .validation(validation)
                .build();
    }

    /**
     * 创建错误响应
     * 
     * @param message 错误消息
     * @return 错误响应
     */
    public static ProcessInvoiceResponse error(String message) {
        return ProcessInvoiceResponse.builder()
                .status("error")
                .message(message)
                .build();
    }

    /**
     * 创建警告响应
     * 
     * @param message 警告消息
     * @param invoice 处理后的发票
     * @param validation 校验结果
     * @return 警告响应
     */
    public static ProcessInvoiceResponse warning(String message, InvoiceDomainObject invoice, ValidationResult validation) {
        return ProcessInvoiceResponse.builder()
                .status("warning")
                .message(message)
                .invoice(invoice)
                .validation(validation)
                .build();
    }
}