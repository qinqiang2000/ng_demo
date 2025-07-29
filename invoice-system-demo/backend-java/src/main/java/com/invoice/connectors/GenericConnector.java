package com.invoice.connectors;

import com.invoice.core.KdublConverter;
import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 通用业务连接器
 * 
 * 与 Python GenericConnector 功能完全等价
 * 处理通用格式的发票数据转换
 */
@Component
@Slf4j
public class GenericConnector extends BaseConnector {

    private final KdublConverter kdublConverter;
    
    public GenericConnector(KdublConverter kdublConverter) {
        super("GenericConnector", "1.0.0");
        this.kdublConverter = kdublConverter;
    }
    
    @Override
    public InvoiceDomainObject transformToStandard(String inputData, String dataType, Map<String, Object> options) {
        if (!isEnabled()) {
            throw new RuntimeException("连接器未启用");
        }
        
        try {
            log.debug("通用连接器开始转换 - 数据类型: {}, 数据长度: {}", dataType, inputData.length());
            
            InvoiceDomainObject result;
            
            switch (dataType.toLowerCase()) {
                case "xml":
                case "kdubl":
                    result = kdublConverter.xmlToDomainObject(inputData);
                    logTransformation("输入转换", dataType, true, 
                        "成功解析 KDUBL XML，发票号: " + result.getInvoiceNumber());
                    break;
                    
                case "text":
                    result = transformTextToStandard(inputData, options);
                    logTransformation("输入转换", dataType, true, 
                        "成功解析文本数据，发票号: " + result.getInvoiceNumber());
                    break;
                    
                case "json":
                    result = transformJsonToStandard(inputData, options);
                    logTransformation("输入转换", dataType, true, 
                        "成功解析 JSON 数据，发票号: " + result.getInvoiceNumber());
                    break;
                    
                default:
                    throw new IllegalArgumentException("不支持的数据类型: " + dataType);
            }
            
            return result;
            
        } catch (Exception e) {
            logTransformation("输入转换", dataType, false, e.getMessage());
            throw new RuntimeException("通用连接器转换失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String transformFromStandard(InvoiceDomainObject invoice, String targetFormat, Map<String, Object> options) {
        if (!isEnabled()) {
            throw new RuntimeException("连接器未启用");
        }
        
        try {
            log.debug("通用连接器开始输出转换 - 目标格式: {}, 发票号: {}", 
                targetFormat, invoice.getInvoiceNumber());
            
            String result;
            
            switch (targetFormat.toLowerCase()) {
                case "xml":
                case "kdubl":
                    result = kdublConverter.domainObjectToXml(invoice);
                    logTransformation("输出转换", targetFormat, true, 
                        "成功生成 KDUBL XML，长度: " + result.length());
                    break;
                    
                case "json":
                    result = transformStandardToJson(invoice, options);
                    logTransformation("输出转换", targetFormat, true, 
                        "成功生成 JSON，长度: " + result.length());
                    break;
                    
                default:
                    throw new IllegalArgumentException("不支持的目标格式: " + targetFormat);
            }
            
            return result;
            
        } catch (Exception e) {
            logTransformation("输出转换", targetFormat, false, e.getMessage());
            throw new RuntimeException("通用连接器输出转换失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean validateInput(String inputData, String dataType) {
        if (inputData == null || inputData.trim().isEmpty()) {
            return false;
        }
        
        try {
            switch (dataType.toLowerCase()) {
                case "xml":
                case "kdubl":
                    return inputData.trim().startsWith("<") && inputData.trim().endsWith(">");
                    
                case "text":
                    return inputData.length() > 10; // 简单的长度检查
                    
                case "json":
                    return (inputData.trim().startsWith("{") && inputData.trim().endsWith("}")) ||
                           (inputData.trim().startsWith("[") && inputData.trim().endsWith("]"));
                    
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warn("输入验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String[] getSupportedDataTypes() {
        return new String[]{"xml", "kdubl", "text", "json"};
    }
    
    @Override
    protected String getDescription() {
        return "通用业务连接器，支持 XML/KDUBL、文本和 JSON 格式的发票数据转换";
    }
    
    /**
     * 将文本数据转换为标准发票对象
     */
    private InvoiceDomainObject transformTextToStandard(String textData, Map<String, Object> options) {
        log.debug("开始解析文本数据，长度: {}", textData.length());
        
        // 解析文本数据中的关键信息
        String invoiceNumber = extractFromText(textData, "发票号[:：]\\s*([^\\n\\r]+)", "TEST-" + System.currentTimeMillis());
        BigDecimal totalAmount = extractAmountFromText(textData, "金额[:：]\\s*([0-9,]+\\.?[0-9]*)", BigDecimal.ZERO);
        String supplierName = extractFromText(textData, "供应商[:：]\\s*([^\\n\\r]+)", null);
        String customerName = extractFromText(textData, "客户[:：]\\s*([^\\n\\r]+)", null);
        
        log.debug("解析结果 - 发票号: {}, 金额: {}, 供应商: {}, 客户: {}", 
            invoiceNumber, totalAmount, supplierName, customerName);
        
        // 创建供应商和客户对象
        Party supplier = null;
        if (supplierName != null && !supplierName.trim().isEmpty()) {
            supplier = Party.builder()
                .name(supplierName.trim())
                .standardName(supplierName.trim())
                .build();
        }
        
        Party customer = null;
        if (customerName != null && !customerName.trim().isEmpty()) {
            customer = Party.builder()
                .name(customerName.trim())
                .standardName(customerName.trim())
                .build();
        }
        
        return InvoiceDomainObject.builder()
            .invoiceNumber(invoiceNumber)
            .totalAmount(totalAmount)
            .currency("CNY")
            .issueDate(LocalDate.now())
            .supplier(supplier)
            .customer(customer)
            .status("processing")
            .notes("由文本数据解析生成: " + 
                (textData.length() > 50 ? textData.substring(0, 50) + "..." : textData))
            .build();
    }
    
    /**
     * 从文本中提取字符串值
     */
    private String extractFromText(String text, String pattern, String defaultValue) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            log.warn("提取文本失败，模式: {}, 错误: {}", pattern, e.getMessage());
        }
        return defaultValue;
    }
    
    /**
     * 从文本中提取金额
     */
    private BigDecimal extractAmountFromText(String text, String pattern, BigDecimal defaultValue) {
        try {
            String amountStr = extractFromText(text, pattern, null);
            if (amountStr != null) {
                // 移除逗号分隔符
                amountStr = amountStr.replaceAll(",", "");
                return new BigDecimal(amountStr);
            }
        } catch (Exception e) {
            log.warn("提取金额失败，模式: {}, 错误: {}", pattern, e.getMessage());
        }
        return defaultValue;
    }
    
    /**
     * 将 JSON 数据转换为标准发票对象
     */
    private InvoiceDomainObject transformJsonToStandard(String jsonData, Map<String, Object> options) {
        log.debug("开始解析 JSON 数据，长度: {}", jsonData.length());
        
        // TODO: 实现真正的 JSON 解析
        // 这里创建一个基础的发票对象作为示例
        return InvoiceDomainObject.builder()
            .invoiceNumber("JSON-" + System.currentTimeMillis())
            .totalAmount(BigDecimal.valueOf(2000.00))
            .currency("CNY")
            .issueDate(LocalDate.now())
            .supplier(createDefaultParty("JSON解析供应商"))
            .customer(createDefaultParty("JSON解析客户"))
            .status("DRAFT")
            .notes("由 JSON 数据解析生成")
            .build();
    }
    
    /**
     * 将标准发票对象转换为 JSON
     */
    private String transformStandardToJson(InvoiceDomainObject invoice, Map<String, Object> options) {
        log.debug("开始生成 JSON，发票号: {}", invoice.getInvoiceNumber());
        
        // 简化的 JSON 生成，实际应用中应该使用 Jackson 等工具
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"invoice_number\": \"").append(invoice.getInvoiceNumber()).append("\",\n");
        json.append("  \"total_amount\": ").append(invoice.getTotalAmount()).append(",\n");
        json.append("  \"currency\": \"").append(invoice.getCurrency()).append("\",\n");
        json.append("  \"issue_date\": \"").append(invoice.getIssueDate()).append("\",\n");
        json.append("  \"status\": \"").append(invoice.getStatus()).append("\",\n");
        
        if (invoice.getSupplier() != null) {
            json.append("  \"supplier\": {\n");
            json.append("    \"name\": \"").append(invoice.getSupplier().getName()).append("\"\n");
            json.append("  },\n");
        }
        
        if (invoice.getCustomer() != null) {
            json.append("  \"customer\": {\n");
            json.append("    \"name\": \"").append(invoice.getCustomer().getName()).append("\"\n");
            json.append("  },\n");
        }
        
        json.append("  \"backend\": \"java-spring-boot\",\n");
        json.append("  \"connector\": \"GenericConnector\"\n");
        json.append("}");
        
        return json.toString();
    }
    
    /**
     * 创建默认参与方对象
     */
    private Party createDefaultParty(String name) {
        return Party.builder()
            .name(name)
            .standardName(name)
            .build();
    }
}