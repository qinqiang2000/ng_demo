package com.invoice.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态文件控制器
 * 
 * 提供与Python后端兼容的静态文件访问路径 /data/**
 * 绕过Spring Boot的context-path限制
 */
@RestController
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class StaticFileController {

    private final ResourceLoader resourceLoader;
    private final Path dataDir;

    public StaticFileController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        
        // 计算共享数据目录的绝对路径
        Path currentDir = Paths.get("").toAbsolutePath();
        Path projectRoot = currentDir.getParent(); // 到达 invoice-system-demo
        this.dataDir = projectRoot.resolve("shared").resolve("data");
        
        log.info("静态文件控制器初始化完成，数据目录: {}", dataDir);
    }

    /**
     * 处理 /data/** 路径的静态文件请求
     * 与Python后端的路径保持一致
     * 
     * @param filename 文件名
     * @return 文件内容
     */
    @GetMapping("/data/{filename}")
    public ResponseEntity<Resource> getDataFile(@PathVariable String filename) {
        try {
            // 构建文件路径
            Path filePath = dataDir.resolve(filename);
            
            // 安全检查：确保请求的文件在数据目录内
            if (!filePath.normalize().startsWith(dataDir.normalize())) {
                log.warn("拒绝访问数据目录外的文件: {}", filename);
                return ResponseEntity.notFound().build();
            }
            
            // 加载资源
            String fileUri = "file:" + filePath.toString();
            Resource resource = resourceLoader.getResource(fileUri);
            
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("文件不存在或不可读: {}", filename);
                return ResponseEntity.notFound().build();
            }
            
            // 确定内容类型
            String contentType = determineContentType(filename);
            
            log.debug("提供静态文件: {} (类型: {})", filename, contentType);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600") // 缓存1小时
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("提供静态文件失败: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据文件扩展名确定内容类型
     */
    private String determineContentType(String filename) {
        String lowerFilename = filename.toLowerCase();
        
        if (lowerFilename.endsWith(".xml")) {
            return MediaType.APPLICATION_XML_VALUE;
        } else if (lowerFilename.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_VALUE;
        } else if (lowerFilename.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN_VALUE;
        } else if (lowerFilename.endsWith(".html")) {
            return MediaType.TEXT_HTML_VALUE;
        } else {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
    
    /**
     * 健康检查端点
     */
    @GetMapping("/data/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Static file controller is healthy. Data directory: " + dataDir);
    }
}