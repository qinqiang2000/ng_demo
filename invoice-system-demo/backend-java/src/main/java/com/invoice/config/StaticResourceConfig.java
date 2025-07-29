package com.invoice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源配置
 * 
 * 配置静态文件服务，等价于 Python FastAPI 的 StaticFiles mount
 * 提供示例数据文件的 HTTP 访问支持
 */
@Configuration
@Slf4j
public class StaticResourceConfig implements WebMvcConfigurer {

    /**
     * 配置静态资源处理器
     * 
     * 映射 /data/** 请求到共享数据目录，与 Python 后端保持一致
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            // 计算共享数据目录的绝对路径
            // 从 backend-java 目录向上定位到项目根目录，然后找到 shared/data
            Path currentDir = Paths.get("").toAbsolutePath();
            Path projectRoot = currentDir.getParent(); // 到达 invoice-system-demo
            Path dataDir = projectRoot.resolve("shared").resolve("data");
            
            // 确保目录存在
            File dataDirFile = dataDir.toFile();
            if (!dataDirFile.exists()) {
                log.warn("共享数据目录不存在: {}", dataDir);
                return;
            }
            
            // 将路径转换为 file:// URL 格式
            String dataResourceLocation = "file:" + dataDir.toString() + "/";
            
            log.info("配置静态资源映射: /data/** -> {}", dataResourceLocation);
            
            // 添加资源处理器映射 - 支持两种路径以保持Python后端兼容性
            // 1. /data/** - 与Python后端兼容
            registry.addResourceHandler("/data/**")
                    .addResourceLocations(dataResourceLocation)
                    .setCachePeriod(3600) // 缓存1小时
                    .resourceChain(true);
            
            // 2. /api/data/** - Java后端默认路径
            registry.addResourceHandler("/api/data/**")
                    .addResourceLocations(dataResourceLocation)
                    .setCachePeriod(3600) // 缓存1小时
                    .resourceChain(true);
                    
            log.info("静态文件服务配置完成，示例数据文件可通过 /data/* 和 /api/data/* 访问");
            
        } catch (Exception e) {
            log.error("配置静态资源映射失败", e);
        }
    }
}