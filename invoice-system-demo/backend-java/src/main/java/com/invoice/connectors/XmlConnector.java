package com.invoice.connectors;

import com.invoice.core.KdublConverter;
import com.invoice.domain.InvoiceDomainObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Map;

/**
 * XML 专用业务连接器
 * 
 * 与 Python XmlConnector 功能完全等价
 * 专门处理各种 XML 格式的发票数据
 */
@Component
@Slf4j
public class XmlConnector extends BaseConnector {

    private final KdublConverter kdublConverter;
    
    public XmlConnector(KdublConverter kdublConverter) {
        super("XmlConnector", "1.0.0");
        this.kdublConverter = kdublConverter;
    }
    
    @Override
    public InvoiceDomainObject transformToStandard(String inputData, String dataType, Map<String, Object> options) {
        if (!isEnabled()) {
            throw new RuntimeException("XML连接器未启用");
        }
        
        try {
            log.debug("XML连接器开始转换 - 数据类型: {}, XML长度: {}", dataType, inputData.length());
            
            // 验证 XML 格式
            if (!validateXmlFormat(inputData)) {
                throw new IllegalArgumentException("无效的 XML 格式");
            }
            
            InvoiceDomainObject result;
            
            // 检测 XML 类型并进行相应转换
            String xmlType = detectXmlType(inputData);
            log.debug("检测到 XML 类型: {}", xmlType);
            
            switch (xmlType) {
                case "kdubl":
                case "ubl":
                    result = kdublConverter.xmlToDomainObject(inputData);
                    logTransformation("KDUBL转换", dataType, true, 
                        "成功解析 KDUBL/UBL XML，发票号: " + result.getInvoiceNumber());
                    break;
                    
                case "generic":
                    result = transformGenericXmlToStandard(inputData, options);
                    logTransformation("通用XML转换", dataType, true, 
                        "成功解析通用 XML，发票号: " + result.getInvoiceNumber());
                    break;
                    
                case "cii":
                    result = transformCiiXmlToStandard(inputData, options);
                    logTransformation("CII转换", dataType, true, 
                        "成功解析 CII XML，发票号: " + result.getInvoiceNumber());
                    break;
                    
                default:
                    throw new IllegalArgumentException("不支持的XML类型: " + xmlType);
            }
            
            // 添加连接器元数据
            enrichWithConnectorMetadata(result, xmlType, options);
            
            return result;
            
        } catch (Exception e) {
            logTransformation("XML转换", dataType, false, e.getMessage());
            throw new RuntimeException("XML连接器转换失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String transformFromStandard(InvoiceDomainObject invoice, String targetFormat, Map<String, Object> options) {
        if (!isEnabled()) {
            throw new RuntimeException("XML连接器未启用");
        }
        
        try {
            log.debug("XML连接器开始输出转换 - 目标格式: {}, 发票号: {}", 
                targetFormat, invoice.getInvoiceNumber());
            
            String result;
            
            switch (targetFormat.toLowerCase()) {
                case "kdubl":
                case "ubl":
                    result = kdublConverter.domainObjectToXml(invoice);
                    logTransformation("KDUBL生成", targetFormat, true, 
                        "成功生成 KDUBL XML，长度: " + result.length());
                    break;
                    
                case "generic_xml":
                    result = transformStandardToGenericXml(invoice, options);
                    logTransformation("通用XML生成", targetFormat, true, 
                        "成功生成通用 XML，长度: " + result.length());
                    break;
                    
                case "cii":
                    result = transformStandardToCiiXml(invoice, options);
                    logTransformation("CII生成", targetFormat, true, 
                        "成功生成 CII XML，长度: " + result.length());
                    break;
                    
                default:
                    throw new IllegalArgumentException("不支持的XML目标格式: " + targetFormat);
            }
            
            return result;
            
        } catch (Exception e) {
            logTransformation("XML输出转换", targetFormat, false, e.getMessage());
            throw new RuntimeException("XML连接器输出转换失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean validateInput(String inputData, String dataType) {
        if (inputData == null || inputData.trim().isEmpty()) {
            return false;
        }
        
        // 基本的 XML 格式检查
        if (!inputData.trim().startsWith("<") || !inputData.trim().endsWith(">")) {
            return false;
        }
        
        return validateXmlFormat(inputData);
    }
    
    @Override
    public String[] getSupportedDataTypes() {
        return new String[]{"xml", "kdubl", "ubl", "cii", "generic_xml"};
    }
    
    @Override
    protected String getDescription() {
        return "XML专用业务连接器，支持KDUBL/UBL、CII和通用XML格式的发票数据转换";
    }
    
    /**
     * 验证 XML 格式
     */
    private boolean validateXmlFormat(String xmlData) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(xmlData)));
            return true;
        } catch (Exception e) {
            log.debug("XML格式验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检测 XML 类型
     */
    private String detectXmlType(String xmlData) {
        String normalizedXml = xmlData.toLowerCase().replaceAll("\\s+", " ");
        
        // KDUBL/UBL 检测
        if (normalizedXml.contains("urn:oasis:names:specification:ubl") || 
            normalizedXml.contains("kdubl") ||
            normalizedXml.contains("invoice") && normalizedXml.contains("cbc:id")) {
            return "kdubl";
        }
        
        // CII 检测 (Cross Industry Invoice)
        if (normalizedXml.contains("crossindustryinvoice") ||
            normalizedXml.contains("urn:un:unece:uncefact:data:standard")) {
            return "cii";
        }
        
        // 通用 XML 检测
        if (normalizedXml.contains("<invoice") || 
            normalizedXml.contains("<发票") ||
            normalizedXml.contains("invoice_number") ||
            normalizedXml.contains("发票号")) {
            return "generic";
        }
        
        return "generic"; // 默认为通用类型
    }
    
    /**
     * 转换通用 XML 为标准发票对象
     */
    private InvoiceDomainObject transformGenericXmlToStandard(String xmlData, Map<String, Object> options) {
        log.info("开始解析通用 XML 数据，XML长度: {}", xmlData.length());
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlData)));
            
            // 解析基本发票信息
            String invoiceNumber = extractTextContent(document, "invoice_number", "invoiceNumber", "number", "编号");
            String invoiceType = extractTextContent(document, "invoice_type", "invoiceType", "type", "类型");
            String issueDate = extractTextContent(document, "issue_date", "issueDate", "date", "日期");
            String totalAmount = extractTextContent(document, "total_amount", "totalAmount", "amount", "金额");
            String taxAmount = extractTextContent(document, "total_tax", "tax_amount", "taxAmount", "税额");
            String currency = extractTextContent(document, "currency", "Currency", "币种");
            
            log.info("解析基本信息 - 发票号: {}, 类型: {}, 日期: {}, 总金额: {}, 税额: {}, 币种: {}", 
                invoiceNumber, invoiceType, issueDate, totalAmount, taxAmount, currency);
            
            // 解析客户信息
            com.invoice.domain.Party customer = null;
            var customerNodes = document.getElementsByTagName("customer");
            if (customerNodes.getLength() > 0) {
                var customerElement = (org.w3c.dom.Element) customerNodes.item(0);
                String customerName = extractTextContentFromElement(customerElement, "name", "n", "客户名称");
                String customerTaxNumber = extractTextContentFromElement(customerElement, "tax_number", "taxNumber", "税号");
                String customerAddress = extractTextContentFromElement(customerElement, "address", "地址");
                String customerPhone = extractTextContentFromElement(customerElement, "phone", "电话");
                
                if (customerName != null) {
                    customer = com.invoice.domain.Party.builder()
                        .name(customerName)
                        .taxNo(customerTaxNumber)
                        .phone(customerPhone)
                        .build();
                }
            }
            
            // 解析供应商信息
            com.invoice.domain.Party supplier = null;
            var supplierNodes = document.getElementsByTagName("supplier");
            if (supplierNodes.getLength() > 0) {
                var supplierElement = (org.w3c.dom.Element) supplierNodes.item(0);
                String supplierName = extractTextContentFromElement(supplierElement, "name", "n", "供应商名称");
                String supplierTaxNumber = extractTextContentFromElement(supplierElement, "tax_number", "taxNumber", "税号");
                String supplierAddress = extractTextContentFromElement(supplierElement, "address", "地址");
                String supplierPhone = extractTextContentFromElement(supplierElement, "phone", "电话");
                
                if (supplierName != null) {
                    supplier = com.invoice.domain.Party.builder()
                        .name(supplierName)
                        .taxNo(supplierTaxNumber)
                        .phone(supplierPhone)
                        .build();
                }
            }
            
            // 解析扩展信息
            java.util.Map<String, Object> extensions = new java.util.HashMap<>();
            var extensionsNodes = document.getElementsByTagName("extensions");
            if (extensionsNodes.getLength() > 0) {
                var extensionsElement = (org.w3c.dom.Element) extensionsNodes.item(0);
                var childNodes = extensionsElement.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); i++) {
                    var node = childNodes.item(i);
                    if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        extensions.put(node.getNodeName(), node.getTextContent());
                    }
                }
            }
            
            // 构建发票对象
            var invoiceBuilder = InvoiceDomainObject.builder()
                .invoiceNumber(invoiceNumber != null ? invoiceNumber : "GENERIC-" + System.currentTimeMillis())
                .invoiceType(invoiceType)
                .issueDate(issueDate != null ? java.time.LocalDate.parse(issueDate) : java.time.LocalDate.now())
                .totalAmount(totalAmount != null ? new java.math.BigDecimal(totalAmount) : java.math.BigDecimal.ZERO)
                .taxAmount(taxAmount != null ? new java.math.BigDecimal(taxAmount) : java.math.BigDecimal.ZERO)
                .currency(currency != null ? currency : "CNY")
                .customer(customer)
                .supplier(supplier)
                .status("IMPORTED")
                .notes("由通用XML解析生成");
            
            var invoice = invoiceBuilder.build();
            log.debug("XML解析完成，发票号: {}, 总金额: {}", invoice.getInvoiceNumber(), invoice.getTotalAmount());
            
            return invoice;
                
        } catch (Exception e) {
            log.error("通用XML解析失败", e);
            throw new RuntimeException("通用XML解析失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转换 CII XML 为标准发票对象
     */
    private InvoiceDomainObject transformCiiXmlToStandard(String xmlData, Map<String, Object> options) {
        log.debug("开始解析 CII XML 数据");
        
        // TODO: 实现 CII XML 的完整解析
        // Cross Industry Invoice 是另一种国际标准
        
        return InvoiceDomainObject.builder()
            .invoiceNumber("CII-" + System.currentTimeMillis())
            .totalAmount(java.math.BigDecimal.ZERO)
            .currency("CNY")
            .issueDate(java.time.LocalDate.now())
            .status("IMPORTED")
            .notes("由CII XML解析生成（待完整实现）")
            .build();
    }
    
    /**
     * 转换标准发票对象为通用 XML
     */
    private String transformStandardToGenericXml(InvoiceDomainObject invoice, Map<String, Object> options) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Invoice>\n");
        xml.append("  <InvoiceNumber>").append(escapeXml(invoice.getInvoiceNumber())).append("</InvoiceNumber>\n");
        xml.append("  <TotalAmount>").append(invoice.getTotalAmount()).append("</TotalAmount>\n");
        xml.append("  <Currency>").append(invoice.getCurrency()).append("</Currency>\n");
        xml.append("  <IssueDate>").append(invoice.getIssueDate()).append("</IssueDate>\n");
        xml.append("  <Status>").append(invoice.getStatus()).append("</Status>\n");
        
        if (invoice.getSupplier() != null) {
            xml.append("  <Supplier>\n");
            xml.append("    <Name>").append(escapeXml(invoice.getSupplier().getName())).append("</Name>\n");
            xml.append("  </Supplier>\n");
        }
        
        if (invoice.getCustomer() != null) {
            xml.append("  <Customer>\n");
            xml.append("    <Name>").append(escapeXml(invoice.getCustomer().getName())).append("</Name>\n");
            xml.append("  </Customer>\n");
        }
        
        xml.append("  <GeneratedBy>XmlConnector</GeneratedBy>\n");
        xml.append("</Invoice>");
        
        return xml.toString();
    }
    
    /**
     * 转换标准发票对象为 CII XML
     */
    private String transformStandardToCiiXml(InvoiceDomainObject invoice, Map<String, Object> options) {
        // TODO: 实现完整的 CII XML 生成
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<CrossIndustryInvoice>\n" +
               "  <!-- CII XML 生成待实现 -->\n" +
               "  <ID>" + invoice.getInvoiceNumber() + "</ID>\n" +
               "</CrossIndustryInvoice>";
    }
    
    /**
     * 从 XML 文档中提取文本内容
     */
    private String extractTextContent(Document document, String... tagNames) {
        for (String tagName : tagNames) {
            try {
                var nodeList = document.getElementsByTagName(tagName);
                if (nodeList.getLength() > 0) {
                    String content = nodeList.item(0).getTextContent();
                    if (content != null && !content.trim().isEmpty()) {
                        return content.trim();
                    }
                }
            } catch (Exception e) {
                log.debug("提取标签 {} 内容失败: {}", tagName, e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * 从指定元素中提取文本内容
     */
    private String extractTextContentFromElement(org.w3c.dom.Element element, String... tagNames) {
        for (String tagName : tagNames) {
            try {
                var nodeList = element.getElementsByTagName(tagName);
                if (nodeList.getLength() > 0) {
                    String content = nodeList.item(0).getTextContent();
                    if (content != null && !content.trim().isEmpty()) {
                        return content.trim();
                    }
                }
            } catch (Exception e) {
                log.debug("从元素中提取标签 {} 内容失败: {}", tagName, e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * XML 字符转义
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
    
    /**
     * 用连接器元数据丰富发票对象
     */
    private void enrichWithConnectorMetadata(InvoiceDomainObject invoice, String xmlType, Map<String, Object> options) {
        // 在实际应用中，可以在这里添加连接器相关的元数据
        log.debug("为发票 {} 添加连接器元数据，XML类型: {}", invoice.getInvoiceNumber(), xmlType);
    }
}