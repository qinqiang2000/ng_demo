package com.invoice.channels;

import com.invoice.domain.InvoiceDomainObject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 渠道基类
 * 
 * 与 Python BaseChannel 功能完全等价
 * 定义统一的渠道处理接口
 */
@Slf4j
public abstract class BaseChannel {

    /**
     * 渠道名称
     */
    protected final String channelName;
    
    /**
     * 渠道版本
     */
    protected final String version;
    
    /**
     * 是否启用
     */
    protected boolean enabled;
    
    /**
     * 渠道配置
     */
    protected Map<String, Object> configuration;
    
    public BaseChannel(String channelName, String version) {
        this.channelName = channelName;
        this.version = version;
        this.enabled = true;
    }
    
    /**
     * 处理发票
     * 
     * @param invoice 发票对象
     * @param options 处理选项
     * @return 处理结果
     */
    public abstract ChannelResult process(InvoiceDomainObject invoice, Map<String, Object> options);
    
    /**
     * 验证发票
     * 
     * @param invoice 发票对象
     * @return 验证结果
     */
    public abstract ValidationResult validate(InvoiceDomainObject invoice);
    
    /**
     * 获取渠道状态
     * 
     * @return 渠道状态
     */
    public abstract ChannelStatus getStatus();
    
    /**
     * 获取支持的操作类型
     * 
     * @return 支持的操作类型
     */
    public abstract String[] getSupportedOperations();
    
    /**
     * 渠道处理结果
     */
    public static class ChannelResult {
        private final String resultId;
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;
        private final LocalDateTime timestamp;
        private final long processingTimeMs;
        
        public ChannelResult(boolean success, String message, Map<String, Object> data, long processingTimeMs) {
            this.resultId = UUID.randomUUID().toString();
            this.success = success;
            this.message = message;
            this.data = data;
            this.timestamp = LocalDateTime.now();
            this.processingTimeMs = processingTimeMs;
        }
        
        // Getters
        public String getResultId() { return resultId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String[] errors;
        private final String[] warnings;
        private final String summary;
        
        public ValidationResult(boolean valid, String[] errors, String[] warnings, String summary) {
            this.valid = valid;
            this.errors = errors != null ? errors : new String[0];
            this.warnings = warnings != null ? warnings : new String[0];
            this.summary = summary;
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String[] getErrors() { return errors; }
        public String[] getWarnings() { return warnings; }
        public String getSummary() { return summary; }
    }
    
    /**
     * 渠道状态
     */
    public enum ChannelStatus {
        ACTIVE("活跃"),
        INACTIVE("非活跃"),
        ERROR("错误"),
        MAINTENANCE("维护中");
        
        private final String description;
        
        ChannelStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 获取渠道元数据
     * 
     * @return 渠道元数据
     */
    public Map<String, Object> getMetadata() {
        return Map.of(
            "name", channelName,
            "version", version,
            "enabled", enabled,
            "status", getStatus().name(),
            "supported_operations", getSupportedOperations(),
            "description", getDescription()
        );
    }
    
    /**
     * 获取渠道描述
     * 
     * @return 渠道描述
     */
    protected abstract String getDescription();
    
    /**
     * 记录处理日志
     * 
     * @param operation 操作类型
     * @param invoiceNumber 发票号
     * @param success 是否成功
     * @param message 消息
     */
    protected void logOperation(String operation, String invoiceNumber, boolean success, String message) {
        if (success) {
            log.info("[{}] {} 操作成功 - 发票号: {}, 消息: {}", 
                channelName, operation, invoiceNumber, message);
        } else {
            log.error("[{}] {} 操作失败 - 发票号: {}, 消息: {}", 
                channelName, operation, invoiceNumber, message);
        }
    }
    
    /**
     * 启用渠道
     */
    public void enable() {
        this.enabled = true;
        log.info("渠道 {} 已启用", channelName);
    }
    
    /**
     * 禁用渠道
     */
    public void disable() {
        this.enabled = false;
        log.warn("渠道 {} 已禁用", channelName);
    }
    
    /**
     * 检查渠道是否启用
     * 
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 设置渠道配置
     * 
     * @param configuration 配置信息
     */
    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
        log.info("渠道 {} 配置已更新", channelName);
    }
    
    /**
     * 获取渠道名称
     * 
     * @return 渠道名称
     */
    public String getChannelName() {
        return channelName;
    }
    
    /**
     * 获取渠道版本
     * 
     * @return 渠道版本
     */
    public String getVersion() {
        return version;
    }
}