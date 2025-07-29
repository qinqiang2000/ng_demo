package com.invoice.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 示例数据控制器
 * 
 * 提供系统示例数据的API接口
 * 与 Python 版本功能完全等价
 */
@RestController
@RequestMapping("/api/sample-data")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@Slf4j
public class SampleDataController {

    /**
     * 获取示例数据列表
     * 
     * @return 示例数据响应
     */
    @GetMapping(produces = "application/json")
    public ResponseEntity<Map<String, Object>> getSampleData() {
        try {
            log.info("获取示例数据列表");
            
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> sampleFiles = new ArrayList<>();
            
            // 加载共享数据目录中的示例文件
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            
            try {
                // 尝试从共享数据目录加载示例文件
                Resource[] resources = resolver.getResources("file:../shared/data/*.xml");
                
                for (Resource resource : resources) {
                    if (resource.exists() && resource.isReadable()) {
                        String filename = resource.getFilename();
                        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("filename", filename);
                        fileInfo.put("name", filename);
                        fileInfo.put("content", content);
                        fileInfo.put("size", content.length());
                        fileInfo.put("type", "xml");
                        
                        // 解析文件描述信息
                        String description = getFileDescription(filename, content);
                        fileInfo.put("description", description);
                        
                        sampleFiles.add(fileInfo);
                        log.debug("加载示例文件: {}, 大小: {} bytes", filename, content.length());
                    }
                }
            } catch (IOException e) {
                log.warn("无法从共享数据目录加载示例文件: {}", e.getMessage());
                
                // 如果无法加载外部文件，提供内置示例数据
                sampleFiles.add(createBuiltinSample());
            }
            
            response.put("success", true);
            response.put("data", sampleFiles);
            response.put("total", sampleFiles.size());
            response.put("message", "示例数据加载成功");
            
            log.info("示例数据加载完成，共 {} 个文件", sampleFiles.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取示例数据失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "获取示例数据失败: " + e.getMessage());
            errorResponse.put("data", new ArrayList<>());
            errorResponse.put("total", 0);
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取指定示例文件内容
     * 
     * @param filename 文件名
     * @return 文件内容
     */
    @GetMapping(value = "/{filename}", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getSampleFile(@PathVariable String filename) {
        try {
            log.info("获取示例文件: {}", filename);
            
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("file:../shared/data/" + filename);
            
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("示例文件不存在或不可读: {}", filename);
                return ResponseEntity.notFound().build();
            }
            
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            Map<String, Object> response = new HashMap<>();
            response.put("name", filename);
            response.put("content", content);
            response.put("size", content.length());
            response.put("type", "xml");
            response.put("description", getFileDescription(filename, content));
            
            log.info("示例文件加载成功: {}, 大小: {} bytes", filename, content.length());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取示例文件失败: {}", filename, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取示例文件失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 创建内置示例数据
     * 
     * @return 内置示例数据
     */
    private Map<String, Object> createBuiltinSample() {
        String sampleContent = """
            <?xml version="1.0" ?>
            <Invoice xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
              <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
              <cbc:CustomizationID>示例发票</cbc:CustomizationID>
              <cbc:ID>SAMPLE-001</cbc:ID>
              <cbc:IssueDate>2025-01-15</cbc:IssueDate>
              <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
              <cbc:DocumentCurrencyCode>CNY</cbc:DocumentCurrencyCode>
              <cac:AccountingSupplierParty>
                <cac:Party>
                  <cbc:Name>示例供应商</cbc:Name>
                </cac:Party>
              </cac:AccountingSupplierParty>
              <cac:AccountingCustomerParty>
                <cac:Party>
                  <cbc:Name>示例客户</cbc:Name>
                </cac:Party>
              </cac:AccountingCustomerParty>
              <cac:InvoiceLine>
                <cbc:ID>1</cbc:ID>
                <cbc:InvoicedQuantity unitCode="EA">1</cbc:InvoicedQuantity>
                <cbc:LineExtensionAmount currencyID="CNY">100.00</cbc:LineExtensionAmount>
                <cac:Item>
                  <cbc:Name>示例商品</cbc:Name>
                </cac:Item>
                <cac:Price>
                  <cbc:PriceAmount currencyID="CNY">100.00</cbc:PriceAmount>
                </cac:Price>
              </cac:InvoiceLine>
              <cac:LegalMonetaryTotal>
                <cbc:LineExtensionAmount currencyID="CNY">100.00</cbc:LineExtensionAmount>
                <cbc:PayableAmount currencyID="CNY">100.00</cbc:PayableAmount>
              </cac:LegalMonetaryTotal>
            </Invoice>
            """;
        
        Map<String, Object> sampleFile = new HashMap<>();
        sampleFile.put("name", "sample.xml");
        sampleFile.put("content", sampleContent);
        sampleFile.put("size", sampleContent.length());
        sampleFile.put("type", "xml");
        sampleFile.put("description", "内置示例发票 - 简单的单行项目发票");
        
        return sampleFile;
    }
    
    /**
     * 获取文件描述信息
     * 
     * @param filename 文件名
     * @param content  文件内容
     * @return 描述信息
     */
    private String getFileDescription(String filename, String content) {
        // 简单的描述生成逻辑
        if (filename.contains("invoice1")) {
            return "多行项目发票示例 - 包含住房、餐饮、停车等多种服务项目";
        } else if (filename.contains("invoice2")) {
            return "简单发票示例 - 单行项目基础发票";
        } else if (filename.contains("invoice3")) {
            return "复杂发票示例 - 包含多种业务场景";
        } else {
            // 根据内容分析生成描述
            int lineCount = content.split("<cac:InvoiceLine>").length - 1;
            if (lineCount > 1) {
                return String.format("多行项目发票 - 包含 %d 个发票行项目", lineCount);
            } else {
                return "单行项目发票示例";
            }
        }
    }
}