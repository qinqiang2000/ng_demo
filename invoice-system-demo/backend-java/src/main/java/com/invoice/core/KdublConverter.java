package com.invoice.core;

import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.Party;
import com.invoice.domain.InvoiceItem;
import com.invoice.domain.Address;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KDUBL XML 转换器
 * 
 * Java 版本的 Python KdublConverter
 * 实现 KDUBL XML 格式与域对象之间的双向转换
 * 基于 UBL 2.1 标准的 Kingdee 扩展格式
 */
@Component
@Slf4j
public class KdublConverter {

    private static final String KDUBL_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CBC_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;
    private final TransformerFactory transformerFactory;
    
    public KdublConverter() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        
        this.xPathFactory = XPathFactory.newInstance();
        this.transformerFactory = TransformerFactory.newInstance();
    }
    
    /**
     * 将 KDUBL XML 字符串转换为域对象
     * 
     * @param xmlData XML 数据
     * @return 发票域对象
     */
    public InvoiceDomainObject xmlToDomainObject(String xmlData) {
        try {
            log.info("开始解析 KDUBL XML，数据长度: {}", xmlData.length());
            log.info("XML数据前200字符: {}", xmlData.length() > 200 ? xmlData.substring(0, 200) : xmlData);
            
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlData.getBytes("UTF-8")));
            
            XPath xpath = xPathFactory.newXPath();
            
            // 设置命名空间上下文
            xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    switch (prefix) {
                        case "cbc": return CBC_NAMESPACE;
                        case "cac": return CAC_NAMESPACE;
                        default: return javax.xml.XMLConstants.NULL_NS_URI;
                    }
                }
                
                @Override
                public String getPrefix(String namespaceURI) {
                    return null;
                }
                
                @Override
                public java.util.Iterator<String> getPrefixes(String namespaceURI) {
                    return null;
                }
            });
            
            InvoiceDomainObject.InvoiceDomainObjectBuilder invoiceBuilder = InvoiceDomainObject.builder();
            
            // 基本发票信息
            String invoiceNumber = getTextContent(xpath, document, "//cbc:ID");
            log.info("解析发票号: {}", invoiceNumber);
            invoiceBuilder.invoiceNumber(invoiceNumber);
            
            invoiceBuilder.issueDate(parseDate(getTextContent(xpath, document, "//cbc:IssueDate")));
            invoiceBuilder.dueDate(parseDate(getTextContent(xpath, document, "//cbc:DueDate")));
            
            // 金额信息 - 支持多种UBL金额字段
            String totalAmountStr = getTextContent(xpath, document, "//cbc:TaxInclusiveAmount");
            log.info("尝试解析TaxInclusiveAmount: {}", totalAmountStr);
            if (totalAmountStr == null || totalAmountStr.isEmpty()) {
                totalAmountStr = getTextContent(xpath, document, "//cbc:PayableAmount");
                log.info("尝试解析PayableAmount: {}", totalAmountStr);
            }
            if (totalAmountStr == null || totalAmountStr.isEmpty()) {
                totalAmountStr = getTextContent(xpath, document, "//cac:LegalMonetaryTotal/cbc:PayableAmount");
                log.info("尝试解析LegalMonetaryTotal/PayableAmount: {}", totalAmountStr);
            }
            if (totalAmountStr == null || totalAmountStr.isEmpty()) {
                totalAmountStr = getTextContent(xpath, document, "//cac:LegalMonetaryTotal/cbc:LineExtensionAmount");
                log.info("尝试解析LegalMonetaryTotal/LineExtensionAmount: {}", totalAmountStr);
            }
            if (totalAmountStr != null && !totalAmountStr.isEmpty()) {
                invoiceBuilder.totalAmount(new BigDecimal(totalAmountStr));
            }
            
            String taxAmountStr = getTextContent(xpath, document, "//cbc:TaxAmount");
            if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
                invoiceBuilder.taxAmount(new BigDecimal(taxAmountStr));
            }
            
            // 货币
            invoiceBuilder.currency(getTextContent(xpath, document, "//cbc:DocumentCurrencyCode"));
            
            // 供应商信息
            Party supplier = parseParty(xpath, document, "//cac:AccountingSupplierParty/cac:Party");
            invoiceBuilder.supplier(supplier);
            
            // 客户信息
            Party customer = parseParty(xpath, document, "//cac:AccountingCustomerParty/cac:Party");
            invoiceBuilder.customer(customer);
            
            // 发票行项目
            List<InvoiceItem> items = parseInvoiceItems(xpath, document);
            invoiceBuilder.items(items);
            
            // 备注 - 支持多种备注字段
            String notes = getTextContent(xpath, document, "//cbc:Note");
            if (notes == null || notes.isEmpty()) {
                notes = getTextContent(xpath, document, "//cac:InvoiceLine/cbc:Note");
            }
            invoiceBuilder.notes(notes);
            
            // 状态
            invoiceBuilder.status("processed");
            
            // 扩展字段 - 解析自定义扩展元素
            Map<String, Object> extensions = parseExtensions(xpath, document);
            invoiceBuilder.extensions(extensions);
            
            InvoiceDomainObject invoice = invoiceBuilder.build();
            log.info("KDUBL XML 解析完成，发票号: {}, 总金额: {}, 供应商: {}", 
                invoice.getInvoiceNumber(), invoice.getTotalAmount(), 
                invoice.getSupplier() != null ? invoice.getSupplier().getName() : "null");
            
            return invoice;
            
        } catch (Exception e) {
            log.error("KDUBL XML 解析失败", e);
            throw new RuntimeException("KDUBL XML 解析失败", e);
        }
    }
    
    /**
     * 将域对象转换为 KDUBL XML 字符串
     * 
     * @param invoice 发票域对象
     * @return XML 字符串
     */
    public String domainObjectToXml(InvoiceDomainObject invoice) {
        try {
            log.debug("开始生成 KDUBL XML，发票号: {}", invoice.getInvoiceNumber());
            
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.newDocument();
            
            // 根元素
            Element invoiceElement = document.createElementNS(KDUBL_NAMESPACE, "Invoice");
            invoiceElement.setAttribute("xmlns", KDUBL_NAMESPACE);
            invoiceElement.setAttribute("xmlns:cbc", CBC_NAMESPACE);
            invoiceElement.setAttribute("xmlns:cac", CAC_NAMESPACE);
            document.appendChild(invoiceElement);
            
            // 基本信息
            appendTextElement(document, invoiceElement, "cbc:ID", invoice.getInvoiceNumber());
            
            if (invoice.getIssueDate() != null) {
                appendTextElement(document, invoiceElement, "cbc:IssueDate", 
                    invoice.getIssueDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            
            if (invoice.getDueDate() != null) {
                appendTextElement(document, invoiceElement, "cbc:DueDate", 
                    invoice.getDueDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            
            // 货币
            if (invoice.getCurrency() != null) {
                appendTextElement(document, invoiceElement, "cbc:DocumentCurrencyCode", invoice.getCurrency());
            }
            
            // 备注
            if (invoice.getNotes() != null) {
                appendTextElement(document, invoiceElement, "cbc:Note", invoice.getNotes());
            }
            
            // 金额信息
            if (invoice.getTotalAmount() != null) {
                Element totalAmountElement = appendTextElement(document, invoiceElement, 
                    "cbc:TaxInclusiveAmount", invoice.getTotalAmount().toString());
                if (invoice.getCurrency() != null) {
                    totalAmountElement.setAttribute("currencyID", invoice.getCurrency());
                }
            }
            
            // 税额
            if (invoice.getTaxAmount() != null) {
                Element taxAmountElement = appendTextElement(document, invoiceElement, 
                    "cbc:TaxAmount", invoice.getTaxAmount().toString());
                if (invoice.getCurrency() != null) {
                    taxAmountElement.setAttribute("currencyID", invoice.getCurrency());
                }
            }
            
            // 供应商
            if (invoice.getSupplier() != null) {
                Element supplierPartyElement = document.createElementNS(CAC_NAMESPACE, "cac:AccountingSupplierParty");
                appendPartyElement(document, supplierPartyElement, invoice.getSupplier());
                invoiceElement.appendChild(supplierPartyElement);
            }
            
            // 客户
            if (invoice.getCustomer() != null) {
                Element customerPartyElement = document.createElementNS(CAC_NAMESPACE, "cac:AccountingCustomerParty");
                appendPartyElement(document, customerPartyElement, invoice.getCustomer());
                invoiceElement.appendChild(customerPartyElement);
            }
            
            // 发票行项目
            if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
                for (InvoiceItem item : invoice.getItems()) {
                    appendInvoiceLineElement(document, invoiceElement, item);
                }
            }
            
            // 扩展字段 - 生成自定义扩展元素
            if (invoice.getExtensions() != null && !invoice.getExtensions().isEmpty()) {
                appendExtensionsElement(document, invoiceElement, invoice.getExtensions());
            }
            
            // 转换为字符串
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            
            String xmlResult = writer.toString();
            log.info("KDUBL XML 生成完成，发票号: {}", invoice.getInvoiceNumber());
            
            return xmlResult;
            
        } catch (Exception e) {
            log.error("KDUBL XML 生成失败", e);
            throw new RuntimeException("KDUBL XML 生成失败", e);
        }
    }
    
    /**
     * 解析 Party（供应商/客户）信息
     */
    private Party parseParty(XPath xpath, Document document, String partyPath) {
        try {
            NodeList partyNodes = (NodeList) xpath.evaluate(partyPath, document, XPathConstants.NODESET);
            if (partyNodes.getLength() == 0) {
                return null;
            }
            
            Party.PartyBuilder partyBuilder = Party.builder();
            
            // 基本信息 - 支持多种UBL名称字段
            String name = getTextContent(xpath, document, partyPath + "/cbc:Name");
            log.debug("尝试解析Party名称 - 路径1: {}, 结果: {}", partyPath + "/cbc:Name", name);
            if (name == null || name.isEmpty()) {
                name = getTextContent(xpath, document, partyPath + "/cac:PartyName/cbc:Name");
                log.debug("尝试解析Party名称 - 路径2: {}, 结果: {}", partyPath + "/cac:PartyName/cbc:Name", name);
            }
            if (name == null || name.isEmpty()) {
                // 尝试更多可能的路径
                name = getTextContent(xpath, document, partyPath + "/../cbc:Name");
                log.debug("尝试解析Party名称 - 路径3: {}, 结果: {}", partyPath + "/../cbc:Name", name);
            }
            log.debug("最终解析的Party名称: {}", name);
            partyBuilder.name(name);
            
            // 税号
            String taxNo = getTextContent(xpath, document, partyPath + "/cac:PartyTaxScheme/cbc:CompanyID");
            if (taxNo == null || taxNo.isEmpty()) {
                taxNo = getTextContent(xpath, document, partyPath + "/cac:PartyLegalEntity/cbc:CompanyID");
            }
            partyBuilder.taxNo(taxNo);
            
            // 联系信息
            partyBuilder.phone(getTextContent(xpath, document, partyPath + "/cac:Contact/cbc:Telephone"));
            partyBuilder.email(getTextContent(xpath, document, partyPath + "/cac:Contact/cbc:ElectronicMail"));
            partyBuilder.legalRepresentative(getTextContent(xpath, document, partyPath + "/cac:Contact/cbc:Name"));
            
            // 地址信息
            Address address = parseAddress(xpath, document, partyPath + "/cac:PostalAddress");
            partyBuilder.address(address);
            
            // 银行信息
            partyBuilder.bankAccount(getTextContent(xpath, document, partyPath + "/cac:PartyLegalEntity/cbc:CompanyID"));
            partyBuilder.bankName(getTextContent(xpath, document, partyPath + "/cac:PartyLegalEntity/cbc:RegistrationName"));
            
            Party party = partyBuilder.build();
            log.debug("解析Party完成: {}", party.getName());
            return party;
            
        } catch (Exception e) {
            log.warn("解析 Party 信息失败: {}", partyPath, e);
            return null;
        }
    }
    
    /**
     * 解析地址信息
     */
    private Address parseAddress(XPath xpath, Document document, String addressPath) {
        try {
            String street = getTextContent(xpath, document, addressPath + "/cbc:StreetName");
            String city = getTextContent(xpath, document, addressPath + "/cbc:CityName");
            String province = getTextContent(xpath, document, addressPath + "/cbc:CountrySubentity");
            String postalCode = getTextContent(xpath, document, addressPath + "/cbc:PostalZone");
            String country = getTextContent(xpath, document, addressPath + "/cac:Country/cbc:IdentificationCode");
            
            if (street == null && city == null && province == null) {
                return null;
            }
            
            return Address.builder()
                    .street(street)
                    .city(city)
                    .state(province)
                    .postalCode(postalCode)
                    .country(country)
                    .build();
                    
        } catch (Exception e) {
            log.warn("解析地址信息失败: {}", addressPath, e);
            return null;
        }
    }
    
    /**
     * 解析发票行项目
     */
    private List<InvoiceItem> parseInvoiceItems(XPath xpath, Document document) {
        List<InvoiceItem> items = new ArrayList<>();
        
        try {
            NodeList itemNodes = (NodeList) xpath.evaluate("//cac:InvoiceLine", document, XPathConstants.NODESET);
            
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Node itemNode = itemNodes.item(i);
                InvoiceItem.InvoiceItemBuilder itemBuilder = InvoiceItem.builder();
                
                // 基本信息
                itemBuilder.description(getTextContent(xpath, itemNode, ".//cbc:Name"));
                
                // 数量
                String quantityStr = getTextContent(xpath, itemNode, ".//cbc:InvoicedQuantity");
                if (quantityStr != null && !quantityStr.isEmpty()) {
                    itemBuilder.quantity(new BigDecimal(quantityStr));
                }
                
                // 单价
                String priceStr = getTextContent(xpath, itemNode, ".//cbc:PriceAmount");
                if (priceStr != null && !priceStr.isEmpty()) {
                    itemBuilder.unitPrice(new BigDecimal(priceStr));
                }
                
                // 金额
                String amountStr = getTextContent(xpath, itemNode, ".//cbc:LineExtensionAmount");
                if (amountStr != null && !amountStr.isEmpty()) {
                    itemBuilder.lineTotal(new BigDecimal(amountStr));
                }
                
                // 税率
                String taxRateStr = getTextContent(xpath, itemNode, ".//cac:TaxCategory/cbc:Percent");
                if (taxRateStr != null && !taxRateStr.isEmpty()) {
                    itemBuilder.taxRate(new BigDecimal(taxRateStr));
                }
                
                items.add(itemBuilder.build());
            }
            
        } catch (Exception e) {
            log.warn("解析发票行项目失败", e);
        }
        
        return items;
    }
    
    /**
     * 添加 Party 元素
     */
    private void appendPartyElement(Document document, Element parentElement, Party party) {
        Element partyElement = document.createElementNS(CAC_NAMESPACE, "cac:Party");
        
        // 名称
        if (party.getName() != null) {
            Element partyNameElement = document.createElementNS(CAC_NAMESPACE, "cac:PartyName");
            appendTextElement(document, partyNameElement, "cbc:Name", party.getName());
            partyElement.appendChild(partyNameElement);
        }
        
        // 地址
        if (party.getAddress() != null) {
            appendAddressElement(document, partyElement, party.getAddress());
        }
        
        // 联系信息
        if (party.getPhone() != null || party.getEmail() != null || party.getLegalRepresentative() != null) {
            Element contactElement = document.createElementNS(CAC_NAMESPACE, "cac:Contact");
            
            if (party.getLegalRepresentative() != null) {
                appendTextElement(document, contactElement, "cbc:Name", party.getLegalRepresentative());
            }
            if (party.getPhone() != null) {
                appendTextElement(document, contactElement, "cbc:Telephone", party.getPhone());
            }
            if (party.getEmail() != null) {
                appendTextElement(document, contactElement, "cbc:ElectronicMail", party.getEmail());
            }
            
            partyElement.appendChild(contactElement);
        }
        
        // 税号
        if (party.getTaxNo() != null) {
            Element taxSchemeElement = document.createElementNS(CAC_NAMESPACE, "cac:PartyTaxScheme");
            appendTextElement(document, taxSchemeElement, "cbc:CompanyID", party.getTaxNo());
            partyElement.appendChild(taxSchemeElement);
        }
        
        parentElement.appendChild(partyElement);
    }
    
    /**
     * 添加地址元素
     */
    private void appendAddressElement(Document document, Element parentElement, Address address) {
        Element addressElement = document.createElementNS(CAC_NAMESPACE, "cac:PostalAddress");
        
        if (address.getStreet() != null) {
            appendTextElement(document, addressElement, "cbc:StreetName", address.getStreet());
        }
        if (address.getCity() != null) {
            appendTextElement(document, addressElement, "cbc:CityName", address.getCity());
        }
        if (address.getState() != null) {
            appendTextElement(document, addressElement, "cbc:CountrySubentity", address.getState());
        }
        if (address.getPostalCode() != null) {
            appendTextElement(document, addressElement, "cbc:PostalZone", address.getPostalCode());
        }
        if (address.getCountry() != null) {
            Element countryElement = document.createElementNS(CAC_NAMESPACE, "cac:Country");
            appendTextElement(document, countryElement, "cbc:IdentificationCode", address.getCountry());
            addressElement.appendChild(countryElement);
        }
        
        parentElement.appendChild(addressElement);
    }
    
    /**
     * 添加发票行项目元素
     */
    private void appendInvoiceLineElement(Document document, Element parentElement, InvoiceItem item) {
        Element lineElement = document.createElementNS(CAC_NAMESPACE, "cac:InvoiceLine");
        
        // 商品信息
        if (item.getDescription() != null) {
            Element itemElement = document.createElementNS(CAC_NAMESPACE, "cac:Item");
            appendTextElement(document, itemElement, "cbc:Name", item.getDescription());
            lineElement.appendChild(itemElement);
        }
        
        // 数量
        if (item.getQuantity() != null) {
            appendTextElement(document, lineElement, "cbc:InvoicedQuantity", item.getQuantity().toString());
        }
        
        // 金额
        if (item.getLineTotal() != null) {
            appendTextElement(document, lineElement, "cbc:LineExtensionAmount", item.getLineTotal().toString());
        }
        
        // 单价
        if (item.getUnitPrice() != null) {
            Element priceElement = document.createElementNS(CAC_NAMESPACE, "cac:Price");
            appendTextElement(document, priceElement, "cbc:PriceAmount", item.getUnitPrice().toString());
            lineElement.appendChild(priceElement);
        }
        
        parentElement.appendChild(lineElement);
    }
    
    /**
     * 添加文本元素
     */
    private Element appendTextElement(Document document, Element parent, String elementName, String textContent) {
        String[] parts = elementName.split(":");
        String namespace = parts[0].equals("cbc") ? CBC_NAMESPACE : CAC_NAMESPACE;
        
        Element element = document.createElementNS(namespace, elementName);
        if (textContent != null) {
            element.setTextContent(textContent);
        }
        parent.appendChild(element);
        return element;
    }
    
    /**
     * 获取文本内容
     */
    private String getTextContent(XPath xpath, Object context, String expression) {
        try {
            Node node = (Node) xpath.evaluate(expression, context, XPathConstants.NODE);
            return node != null ? node.getTextContent().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 解析日期
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.warn("日期解析失败: {}", dateStr, e);
            return null;
        }
    }
    
    /**
     * 解析扩展字段
     * 从XML中提取自定义扩展元素到Map中
     */
    private Map<String, Object> parseExtensions(XPath xpath, Document document) {
        Map<String, Object> extensions = new HashMap<>();
        
        try {
            // 解析扩展字段容器
            NodeList extensionNodes = (NodeList) xpath.evaluate("//cac:AdditionalDocumentReference", document, XPathConstants.NODESET);
            
            for (int i = 0; i < extensionNodes.getLength(); i++) {
                Node extensionNode = extensionNodes.item(i);
                
                // 获取扩展字段的ID作为key
                String extensionId = getTextContent(xpath, extensionNode, ".//cbc:ID");
                // 获取扩展字段的值
                String extensionValue = getTextContent(xpath, extensionNode, ".//cbc:DocumentDescription");
                
                if (extensionId != null && !extensionId.isEmpty()) {
                    // 尝试解析为数字类型
                    if (extensionValue != null && extensionValue.matches("^\\d+(\\.\\d+)?$")) {
                        try {
                            if (extensionValue.contains(".")) {
                                extensions.put(extensionId, new BigDecimal(extensionValue));
                            } else {
                                extensions.put(extensionId, Integer.parseInt(extensionValue));
                            }
                        } catch (NumberFormatException e) {
                            extensions.put(extensionId, extensionValue);
                        }
                    } else {
                        extensions.put(extensionId, extensionValue);
                    }
                }
            }
            
            // 解析其他常见的扩展字段
            String supplierCategory = getTextContent(xpath, document, "//cac:AccountingSupplierParty//cbc:IndustryClassificationCode");
            if (supplierCategory != null && !supplierCategory.isEmpty()) {
                extensions.put("supplier_category", supplierCategory);
            }
            
            String invoiceCategory = getTextContent(xpath, document, "//cbc:InvoiceTypeCode/@name");
            if (invoiceCategory != null && !invoiceCategory.isEmpty()) {
                extensions.put("invoice_category", invoiceCategory);
            }
            
            log.debug("解析到 {} 个扩展字段", extensions.size());
            
        } catch (Exception e) {
            log.warn("解析扩展字段时出错", e);
        }
        
        return extensions;
    }
    
    /**
     * 生成扩展字段XML元素
     * 将Map中的扩展字段转换为XML元素
     */
    private void appendExtensionsElement(Document document, Element parentElement, Map<String, Object> extensions) {
        try {
            for (Map.Entry<String, Object> entry : extensions.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value == null) {
                    continue;
                }
                
                // 创建AdditionalDocumentReference元素来存储扩展字段
                Element extensionElement = document.createElementNS(CAC_NAMESPACE, "cac:AdditionalDocumentReference");
                
                // 扩展字段的ID
                appendTextElement(document, extensionElement, "cbc:ID", key);
                
                // 扩展字段的值
                appendTextElement(document, extensionElement, "cbc:DocumentDescription", value.toString());
                
                // 扩展字段的类型信息
                String valueType = value.getClass().getSimpleName();
                appendTextElement(document, extensionElement, "cbc:DocumentTypeCode", valueType);
                
                parentElement.appendChild(extensionElement);
            }
            
            log.debug("生成了 {} 个扩展字段XML元素", extensions.size());
            
        } catch (Exception e) {
            log.warn("生成扩展字段XML时出错", e);
        }
    }
}