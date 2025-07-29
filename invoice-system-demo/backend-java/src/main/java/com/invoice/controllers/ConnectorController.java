package com.invoice.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接器控制器
 * 
 * 提供系统连接器信息的API接口
 * 与 Python 版本功能完全等价
 */
@RestController
@RequestMapping("/api/connectors")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@Slf4j
public class ConnectorController {

    /**
     * 获取所有可用连接器
     * 
     * @return 连接器列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConnectors() {
        try {
            log.info("获取连接器列表");
            
            List<Map<String, Object>> connectors = new ArrayList<>();
            
            // Generic Connector
            Map<String, Object> genericConnector = new HashMap<>();
            genericConnector.put("id", "generic");
            genericConnector.put("name", "通用连接器");
            genericConnector.put("description", "支持多种数据格式的通用连接器，包括文本、JSON等");
            genericConnector.put("supportedFormats", List.of("text", "json"));
            genericConnector.put("version", "1.0.0");
            genericConnector.put("status", "active");
            genericConnector.put("type", "builtin");
            connectors.add(genericConnector);
            
            // XML Connector
            Map<String, Object> xmlConnector = new HashMap<>();
            xmlConnector.put("id", "xml");
            xmlConnector.put("name", "XML连接器");
            xmlConnector.put("description", "专门处理KDUBL XML格式的连接器");
            xmlConnector.put("supportedFormats", List.of("xml", "kdubl"));
            xmlConnector.put("version", "1.0.0");
            xmlConnector.put("status", "active");
            xmlConnector.put("type", "builtin");
            connectors.add(xmlConnector);
            
            // Base Connector (示例)
            Map<String, Object> baseConnector = new HashMap<>();
            baseConnector.put("id", "base");
            baseConnector.put("name", "基础连接器");
            baseConnector.put("description", "连接器基类，提供通用功能");
            baseConnector.put("supportedFormats", List.of("any"));
            baseConnector.put("version", "1.0.0");
            baseConnector.put("status", "abstract");
            baseConnector.put("type", "base");
            connectors.add(baseConnector);
            
            Map<String, Object> response = new HashMap<>();
            response.put("connectors", connectors);
            response.put("total", connectors.size());
            response.put("message", "连接器列表获取成功");
            
            log.info("连接器列表获取完成，共 {} 个连接器", connectors.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取连接器列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取连接器列表失败: " + e.getMessage());
            errorResponse.put("connectors", new ArrayList<>());
            errorResponse.put("total", 0);
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取指定连接器信息
     * 
     * @param connectorId 连接器ID
     * @return 连接器详细信息
     */
    @GetMapping("/{connectorId}")
    public ResponseEntity<Map<String, Object>> getConnector(@PathVariable String connectorId) {
        try {
            log.info("获取连接器信息: {}", connectorId);
            
            Map<String, Object> connector = new HashMap<>();
            
            switch (connectorId.toLowerCase()) {
                case "generic":
                    connector.put("id", "generic");
                    connector.put("name", "通用连接器");
                    connector.put("description", "支持多种数据格式的通用连接器，包括文本、JSON等");
                    connector.put("supportedFormats", List.of("text", "json"));
                    connector.put("version", "1.0.0");
                    connector.put("status", "active");
                    connector.put("type", "builtin");
                    connector.put("className", "com.invoice.connectors.GenericConnector");
                    connector.put("capabilities", Map.of(
                        "textParsing", true,
                        "jsonParsing", true,
                        "validation", true,
                        "transformation", true
                    ));
                    break;
                    
                case "xml":
                    connector.put("id", "xml");
                    connector.put("name", "XML连接器");
                    connector.put("description", "专门处理KDUBL XML格式的连接器");
                    connector.put("supportedFormats", List.of("xml", "kdubl"));
                    connector.put("version", "1.0.0");
                    connector.put("status", "active");
                    connector.put("type", "builtin");
                    connector.put("className", "com.invoice.connectors.XmlConnector");
                    connector.put("capabilities", Map.of(
                        "xmlParsing", true,
                        "kdublSupport", true,
                        "validation", true,
                        "transformation", true
                    ));
                    break;
                    
                case "base":
                    connector.put("id", "base");
                    connector.put("name", "基础连接器");
                    connector.put("description", "连接器基类，提供通用功能");
                    connector.put("supportedFormats", List.of("any"));
                    connector.put("version", "1.0.0");
                    connector.put("status", "abstract");
                    connector.put("type", "base");
                    connector.put("className", "com.invoice.connectors.BaseConnector");
                    connector.put("capabilities", Map.of(
                        "logging", true,
                        "errorHandling", true,
                        "baseTransformation", true
                    ));
                    break;
                    
                default:
                    log.warn("未找到连接器: {}", connectorId);
                    return ResponseEntity.notFound().build();
            }
            
            log.info("连接器信息获取成功: {}", connectorId);
            return ResponseEntity.ok(connector);
            
        } catch (Exception e) {
            log.error("获取连接器信息失败: {}", connectorId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取连接器信息失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取连接器统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getConnectorStats() {
        try {
            log.info("获取连接器统计信息");
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalConnectors", 3);
            stats.put("activeConnectors", 2);
            stats.put("abstractConnectors", 1);
            
            Map<String, Integer> typeStats = new HashMap<>();
            typeStats.put("builtin", 2);
            typeStats.put("base", 1);
            typeStats.put("custom", 0);
            stats.put("typeDistribution", typeStats);
            
            Map<String, Integer> formatStats = new HashMap<>();
            formatStats.put("text", 1);
            formatStats.put("json", 1);
            formatStats.put("xml", 1);
            formatStats.put("kdubl", 1);
            stats.put("supportedFormats", formatStats);
            
            log.info("连接器统计信息获取完成");
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("获取连接器统计信息失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取连接器统计信息失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}