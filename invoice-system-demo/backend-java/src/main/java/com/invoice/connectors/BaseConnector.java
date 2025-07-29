package com.invoice.connectors;

import com.invoice.domain.InvoiceDomainObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 业务连接器基类
 * 
 * 与 Python BaseConnector 功能完全等价
 * 定义统一的数据转换接口
 */
@Slf4j
public abstract class BaseConnector {

    /**
     * 连接器名称
     */
    protected final String connectorName;
    
    /**
     * 连接器版本
     */
    protected final String version;
    
    /**
     * 是否启用
     */
    protected boolean enabled;
    
    public BaseConnector(String connectorName, String version) {
        this.connectorName = connectorName;
        this.version = version;
        this.enabled = true;
    }
    
    /**
     * 将外部数据转换为标准发票领域对象
     * 
     * @param inputData 输入数据
     * @param dataType 数据类型
     * @param options 转换选项
     * @return 标准发票对象
     */
    public abstract InvoiceDomainObject transformToStandard(
        String inputData, 
        String dataType, 
        Map<String, Object> options
    );
    
    /**
     * 将标准发票领域对象转换为外部格式
     * 
     * @param invoice 标准发票对象
     * @param targetFormat 目标格式
     * @param options 转换选项
     * @return 外部格式数据
     */
    public abstract String transformFromStandard(
        InvoiceDomainObject invoice, 
        String targetFormat, 
        Map<String, Object> options
    );
    
    /**
     * 验证输入数据格式
     * 
     * @param inputData 输入数据
     * @param dataType 数据类型
     * @return 验证结果
     */
    public abstract boolean validateInput(String inputData, String dataType);
    
    /**
     * 获取支持的数据类型
     * 
     * @return 支持的数据类型列表
     */
    public abstract String[] getSupportedDataTypes();
    
    /**
     * 获取连接器元数据
     * 
     * @return 连接器元数据
     */
    public Map<String, Object> getMetadata() {
        return Map.of(
            "name", connectorName,
            "version", version,
            "enabled", enabled,
            "supported_types", getSupportedDataTypes(),
            "description", getDescription()
        );
    }
    
    /**
     * 获取连接器描述
     * 
     * @return 连接器描述
     */
    protected abstract String getDescription();
    
    /**
     * 记录转换日志
     * 
     * @param operation 操作类型
     * @param dataType 数据类型
     * @param success 是否成功
     * @param message 消息
     */
    protected void logTransformation(String operation, String dataType, boolean success, String message) {
        if (success) {
            log.info("[{}] {} 转换成功 - 数据类型: {}, 消息: {}", 
                connectorName, operation, dataType, message);
        } else {
            log.error("[{}] {} 转换失败 - 数据类型: {}, 消息: {}", 
                connectorName, operation, dataType, message);
        }
    }
    
    /**
     * 启用连接器
     */
    public void enable() {
        this.enabled = true;
        log.info("连接器 {} 已启用", connectorName);
    }
    
    /**
     * 禁用连接器
     */
    public void disable() {
        this.enabled = false;
        log.warn("连接器 {} 已禁用", connectorName);
    }
    
    /**
     * 检查连接器是否启用
     * 
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 获取连接器名称
     * 
     * @return 连接器名称
     */
    public String getConnectorName() {
        return connectorName;
    }
    
    /**
     * 获取连接器版本
     * 
     * @return 连接器版本
     */
    public String getVersion() {
        return version;
    }
}