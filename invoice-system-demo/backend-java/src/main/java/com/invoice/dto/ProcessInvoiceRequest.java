package com.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 发票处理请求 DTO
 * 
 * 与 Python FastAPI 的请求模型完全等价
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInvoiceRequest {

    /**
     * 数据类型
     * 
     * 可选值：xml, text, json
     */
    @JsonProperty("data_type")
    @Pattern(regexp = "^(xml|text|json)$", message = "数据类型必须是 xml、text 或 json")
    private String dataType;

    /**
     * 发票数据
     * 
     * 根据 dataType 的不同，可能是 XML 字符串、文本或 JSON 字符串
     */
    @JsonProperty("data")
    private String data;

    /**
     * KDUBL XML 数据 (兼容前端格式)
     */
    @JsonProperty("kdubl_xml")
    private String kdublXml;

    /**
     * KDUBL 列表数据 (兼容前端格式)
     */
    @JsonProperty("kdubl_list")
    private String[] kdublList;

    /**
     * 源系统 (兼容前端格式)
     */
    @JsonProperty("source_system")
    private String sourceSystem;

    /**
     * 连接器类型
     * 
     * 可选参数，用于指定使用哪种连接器处理数据
     */
    @JsonProperty("connector_type")
    private String connectorType;

    /**
     * 处理选项
     * 
     * 额外的处理配置参数
     */
    @JsonProperty("options")
    private ProcessingOptions options;

    /**
     * 处理选项内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingOptions {

        /**
         * 是否跳过字段补全
         */
        @JsonProperty("skip_completion")
        @Builder.Default
        private Boolean skipCompletion = false;

        /**
         * 是否跳过业务校验
         */
        @JsonProperty("skip_validation")
        @Builder.Default
        private Boolean skipValidation = false;

        /**
         * 是否启用详细日志
         */
        @JsonProperty("verbose_logging")
        @Builder.Default
        private Boolean verboseLogging = false;

        /**
         * 超时时间（秒）
         */
        @JsonProperty("timeout_seconds")
        @Builder.Default
        private Integer timeoutSeconds = 30;

        /**
         * 是否返回中间步骤结果
         */
        @JsonProperty("return_intermediate_results")
        @Builder.Default
        private Boolean returnIntermediateResults = false;

        /**
         * 自定义规则集
         * 
         * 可以指定使用特定的规则集进行处理
         */
        @JsonProperty("custom_rule_set")
        private String customRuleSet;

        /**
         * 是否强制重新处理
         * 
         * 即使缓存中有结果也重新处理
         */
        @JsonProperty("force_reprocess")
        @Builder.Default
        private Boolean forceReprocess = false;
    }

    /**
     * 检查是否跳过字段补全
     * 
     * @return 是否跳过字段补全
     */
    public boolean shouldSkipCompletion() {
        return options != null && 
               options.getSkipCompletion() != null && 
               options.getSkipCompletion();
    }

    /**
     * 检查是否跳过业务校验
     * 
     * @return 是否跳过业务校验
     */
    public boolean shouldSkipValidation() {
        return options != null && 
               options.getSkipValidation() != null && 
               options.getSkipValidation();
    }

    /**
     * 检查是否启用详细日志
     * 
     * @return 是否启用详细日志
     */
    public boolean isVerboseLoggingEnabled() {
        return options != null && 
               options.getVerboseLogging() != null && 
               options.getVerboseLogging();
    }

    /**
     * 获取超时时间
     * 
     * @return 超时时间（秒）
     */
    public int getTimeoutSeconds() {
        return options != null && options.getTimeoutSeconds() != null 
               ? options.getTimeoutSeconds() : 30;
    }

    /**
     * 检查是否返回中间步骤结果
     * 
     * @return 是否返回中间步骤结果
     */
    public boolean shouldReturnIntermediateResults() {
        return options != null && 
               options.getReturnIntermediateResults() != null && 
               options.getReturnIntermediateResults();
    }

    /**
     * 检查是否有自定义规则集
     * 
     * @return 是否有自定义规则集
     */
    public boolean hasCustomRuleSet() {
        return options != null && 
               options.getCustomRuleSet() != null && 
               !options.getCustomRuleSet().trim().isEmpty();
    }

    /**
     * 获取自定义规则集名称
     * 
     * @return 自定义规则集名称
     */
    public String getCustomRuleSet() {
        return hasCustomRuleSet() ? options.getCustomRuleSet() : null;
    }

    /**
     * 检查是否强制重新处理
     * 
     * @return 是否强制重新处理
     */
    public boolean shouldForceReprocess() {
        return options != null && 
               options.getForceReprocess() != null && 
               options.getForceReprocess();
    }

    /**
     * 检查数据类型是否为 XML
     * 
     * @return 是否为 XML
     */
    public boolean isXmlData() {
        return "xml".equalsIgnoreCase(dataType);
    }

    /**
     * 检查数据类型是否为 Text
     * 
     * @return 是否为 Text  
     */
    public boolean isTextData() {
        return "text".equalsIgnoreCase(dataType);
    }

    /**
     * 检查数据类型是否为 JSON
     * 
     * @return 是否为 JSON
     */
    public boolean isJsonData() {
        return "json".equalsIgnoreCase(dataType);
    }

    /**
     * 获取实际的数据内容 (兼容前端格式)
     * 
     * @return 数据内容
     */
    public String getActualData() {
        if (data != null) {
            return data;
        }
        if (kdublXml != null) {
            return kdublXml;
        }
        if (kdublList != null && kdublList.length > 0) {
            return kdublList[0]; // 取第一个
        }
        return null;
    }

    /**
     * 获取实际的数据类型 (兼容前端格式)
     * 
     * @return 数据类型
     */
    public String getActualDataType() {
        if (dataType != null) {
            return dataType;
        }
        if (kdublXml != null || (kdublList != null && kdublList.length > 0)) {
            return "xml";
        }
        return "text"; // 默认
    }

    /**
     * 获取数据长度
     * 
     * @return 数据长度
     */
    public int getDataLength() {
        String actualData = getActualData();
        return actualData != null ? actualData.length() : 0;
    }

    /**
     * 检查数据是否为空
     * 
     * @return 数据是否为空
     */
    public boolean hasEmptyData() {
        return data == null || data.trim().isEmpty();
    }
}