package com.invoice.core;

import com.invoice.models.Company;
import com.invoice.models.Product;
import com.invoice.repository.CompanyRepository;
import com.invoice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能查询系统
 * 
 * Java 版本的 Python SmartQuerySystem
 * 提供灵活的数据库查询和匹配功能
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartQuerySystem {

    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    
    /**
     * 查询结果
     */
    public static class QueryResult {
        private final List<Map<String, Object>> results;
        private final int totalCount;
        private final String queryType;
        private final long executionTimeMs;
        
        public QueryResult(List<Map<String, Object>> results, String queryType, long executionTimeMs) {
            this.results = results != null ? results : new ArrayList<>();
            this.totalCount = this.results.size();
            this.queryType = queryType;
            this.executionTimeMs = executionTimeMs;
        }
        
        public List<Map<String, Object>> getResults() { return results; }
        public int getTotalCount() { return totalCount; }
        public String getQueryType() { return queryType; }
        public long getExecutionTimeMs() { return executionTimeMs; }
    }
    
    /**
     * 智能公司查询
     * 
     * @param keyword 关键词
     * @param category 类别
     * @param limit 结果限制
     * @return 查询结果
     */
    public QueryResult queryCompanies(String keyword, String category, Integer limit) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("智能公司查询 - 关键词: {}, 类别: {}, 限制: {}", keyword, category, limit);
            
            List<Company> companies = new ArrayList<>();
            
            // 按优先级查询
            if (keyword != null && !keyword.trim().isEmpty()) {
                if (category != null && !category.trim().isEmpty()) {
                    // 同时按关键词和类别查询
                    companies = companyRepository.findByNameContainingIgnoreCaseAndCategoryOrderByName(
                        keyword.trim(), category.trim());
                } else {
                    // 只按关键词查询
                    companies = companyRepository.findByNameContainingIgnoreCaseOrderByName(keyword.trim());
                }
            } else if (category != null && !category.trim().isEmpty()) {
                // 只按类别查询
                companies = companyRepository.findByCategoryOrderByName(category.trim());
            } else {
                // 查询所有
                companies = companyRepository.findAllByOrderByName();
            }
            
            // 应用限制
            if (limit != null && limit > 0 && companies.size() > limit) {
                companies = companies.subList(0, limit);
            }
            
            // 转换为结果格式
            List<Map<String, Object>> results = companies.stream()
                .map(this::companyToMap)
                .collect(Collectors.toList());
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("公司查询完成 - 找到 {} 个结果，耗时 {}ms", results.size(), executionTime);
            
            return new QueryResult(results, "company_query", executionTime);
            
        } catch (Exception e) {
            log.error("公司查询失败", e);
            return new QueryResult(new ArrayList<>(), "company_query_error", 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * 智能产品查询
     * 
     * @param keyword 关键词
     * @param category 类别
     * @param limit 结果限制
     * @return 查询结果
     */
    public QueryResult queryProducts(String keyword, String category, Integer limit) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("智能产品查询 - 关键词: {}, 类别: {}, 限制: {}", keyword, category, limit);
            
            List<Product> products = new ArrayList<>();
            
            // 按优先级查询
            if (keyword != null && !keyword.trim().isEmpty()) {
                if (category != null && !category.trim().isEmpty()) {
                    // 同时按关键词和类别查询
                    products = productRepository.findByNameContainingIgnoreCaseAndCategoryOrderByName(
                        keyword.trim(), category.trim());
                } else {
                    // 只按关键词查询（名称或描述）
                    products = productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByName(
                        keyword.trim(), keyword.trim());
                }
            } else if (category != null && !category.trim().isEmpty()) {
                // 只按类别查询
                products = productRepository.findByCategoryOrderByName(category.trim());
            } else {
                // 查询所有
                products = productRepository.findAllByOrderByName();
            }
            
            // 应用限制
            if (limit != null && limit > 0 && products.size() > limit) {
                products = products.subList(0, limit);
            }
            
            // 转换为结果格式
            List<Map<String, Object>> results = products.stream()
                .map(this::productToMap)
                .collect(Collectors.toList());
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("产品查询完成 - 找到 {} 个结果，耗时 {}ms", results.size(), executionTime);
            
            return new QueryResult(results, "product_query", executionTime);
            
        } catch (Exception e) {
            log.error("产品查询失败", e);
            return new QueryResult(new ArrayList<>(), "product_query_error", 
                System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * 模糊匹配公司
     * 
     * @param companyName 公司名称
     * @param threshold 相似度阈值 (0.0 - 1.0)
     * @return 最佳匹配结果
     */
    public Map<String, Object> fuzzyMatchCompany(String companyName, Double threshold) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return null;
        }
        
        try {
            log.debug("模糊匹配公司 - 名称: {}, 阈值: {}", companyName, threshold);
            
            List<Company> allCompanies = companyRepository.findAll();
            
            String normalizedInput = normalizeString(companyName);
            double bestScore = 0.0;
            Company bestMatch = null;
            
            for (Company company : allCompanies) {
                String normalizedName = normalizeString(company.getName());
                double similarity = calculateStringSimilarity(normalizedInput, normalizedName);
                
                if (similarity > bestScore) {
                    bestScore = similarity;
                    bestMatch = company;
                }
            }
            
            // 检查是否达到阈值
            double minThreshold = threshold != null ? threshold : 0.6;
            if (bestScore >= minThreshold && bestMatch != null) {
                Map<String, Object> result = companyToMap(bestMatch);
                result.put("similarity_score", bestScore);
                result.put("match_confidence", getConfidenceLevel(bestScore));
                
                log.info("模糊匹配成功 - 输入: '{}', 匹配: '{}', 相似度: {:.2f}", 
                    companyName, bestMatch.getName(), bestScore);
                
                return result;
            }
            
            log.debug("模糊匹配失败 - 输入: '{}', 最高相似度: {:.2f}, 阈值: {:.2f}", 
                companyName, bestScore, minThreshold);
            
            return null;
            
        } catch (Exception e) {
            log.error("模糊匹配公司失败", e);
            return null;
        }
    }
    
    /**
     * 模糊匹配产品
     * 
     * @param productName 产品名称
     * @param threshold 相似度阈值
     * @return 最佳匹配结果
     */
    public Map<String, Object> fuzzyMatchProduct(String productName, Double threshold) {
        if (productName == null || productName.trim().isEmpty()) {
            return null;
        }
        
        try {
            log.debug("模糊匹配产品 - 名称: {}, 阈值: {}", productName, threshold);
            
            List<Product> allProducts = productRepository.findAll();
            
            String normalizedInput = normalizeString(productName);
            double bestScore = 0.0;
            Product bestMatch = null;
            
            for (Product product : allProducts) {
                // 检查产品名称相似度
                String normalizedName = normalizeString(product.getName());
                double nameSimilarity = calculateStringSimilarity(normalizedInput, normalizedName);
                
                // 检查描述相似度
                double descSimilarity = 0.0;
                if (product.getDescription() != null) {
                    String normalizedDesc = normalizeString(product.getDescription());
                    descSimilarity = calculateStringSimilarity(normalizedInput, normalizedDesc);
                }
                
                // 取最高相似度
                double similarity = Math.max(nameSimilarity, descSimilarity);
                
                if (similarity > bestScore) {
                    bestScore = similarity;
                    bestMatch = product;
                }
            }
            
            // 检查是否达到阈值
            double minThreshold = threshold != null ? threshold : 0.6;
            if (bestScore >= minThreshold && bestMatch != null) {
                Map<String, Object> result = productToMap(bestMatch);
                result.put("similarity_score", bestScore);
                result.put("match_confidence", getConfidenceLevel(bestScore));
                
                log.info("模糊匹配成功 - 输入: '{}', 匹配: '{}', 相似度: {:.2f}", 
                    productName, bestMatch.getName(), bestScore);
                
                return result;
            }
            
            log.debug("模糊匹配失败 - 输入: '{}', 最高相似度: {:.2f}, 阈值: {:.2f}", 
                productName, bestScore, minThreshold);
            
            return null;
            
        } catch (Exception e) {
            log.error("模糊匹配产品失败", e);
            return null;
        }
    }
    
    /**
     * 批量查询
     * 
     * @param queries 查询列表
     * @return 批量查询结果
     */
    public Map<String, Object> batchQuery(List<Map<String, Object>> queries) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("开始批量查询，查询数量: {}", queries.size());
            
            Map<String, Object> batchResult = new HashMap<>();
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int errorCount = 0;
            
            for (int i = 0; i < queries.size(); i++) {
                Map<String, Object> query = queries.get(i);
                String queryType = (String) query.get("type");
                
                try {
                    Map<String, Object> queryResult = new HashMap<>();
                    queryResult.put("index", i);
                    queryResult.put("type", queryType);
                    
                    if ("company".equals(queryType)) {
                        QueryResult result = queryCompanies(
                            (String) query.get("keyword"),
                            (String) query.get("category"),
                            (Integer) query.get("limit")
                        );
                        queryResult.put("data", result.getResults());
                        queryResult.put("count", result.getTotalCount());
                        
                    } else if ("product".equals(queryType)) {
                        QueryResult result = queryProducts(
                            (String) query.get("keyword"),
                            (String) query.get("category"),
                            (Integer) query.get("limit")
                        );
                        queryResult.put("data", result.getResults());
                        queryResult.put("count", result.getTotalCount());
                        
                    } else {
                        queryResult.put("error", "不支持的查询类型: " + queryType);
                        errorCount++;
                        continue;
                    }
                    
                    results.add(queryResult);
                    successCount++;
                    
                } catch (Exception e) {
                    log.warn("批量查询中的单项查询失败，索引: {}", i, e);
                    
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("index", i);
                    errorResult.put("error", e.getMessage());
                    results.add(errorResult);
                    errorCount++;
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            batchResult.put("results", results);
            batchResult.put("total_queries", queries.size());
            batchResult.put("success_count", successCount);
            batchResult.put("error_count", errorCount);
            batchResult.put("execution_time_ms", executionTime);
            
            log.info("批量查询完成 - 总数: {}, 成功: {}, 失败: {}, 耗时: {}ms", 
                queries.size(), successCount, errorCount, executionTime);
            
            return batchResult;
            
        } catch (Exception e) {
            log.error("批量查询失败", e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "批量查询失败: " + e.getMessage());
            errorResult.put("execution_time_ms", System.currentTimeMillis() - startTime);
            
            return errorResult;
        }
    }
    
    /**
     * 获取查询统计信息
     */
    public Map<String, Object> getQueryStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long companyCount = companyRepository.count();
            long productCount = productRepository.count();
            
            stats.put("company_count", companyCount);
            stats.put("product_count", productCount);
            stats.put("total_records", companyCount + productCount);
            
            // 公司类别统计
            List<Company> companies = companyRepository.findAll();
            Map<String, Long> categoryStats = companies.stream()
                .filter(c -> c.getCategory() != null)
                .collect(Collectors.groupingBy(
                    Company::getCategory,
                    Collectors.counting()
                ));
            stats.put("company_categories", categoryStats);
            
            // 产品类别统计
            List<Product> products = productRepository.findAll();
            Map<String, Long> productCategoryStats = products.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(
                    Product::getCategory,
                    Collectors.counting()
                ));
            stats.put("product_categories", productCategoryStats);
            
        } catch (Exception e) {
            log.error("获取查询统计信息失败", e);
            stats.put("error", "统计信息获取失败: " + e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * 将公司转换为 Map
     */
    private Map<String, Object> companyToMap(Company company) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", company.getId());
        map.put("name", company.getName());
        map.put("category", company.getCategory());
        map.put("tax_no", company.getTaxNumber());
        map.put("address", company.getAddress());
        map.put("phone", company.getPhone());
        map.put("email", company.getEmail());
        map.put("contact_person", null); // Company model doesn't have contact_person field
        map.put("bank_account", null); // Company model doesn't have bank_account field
        map.put("bank_name", null); // Company model doesn't have bank_name field
        map.put("status", company.getIsActive() ? "active" : "inactive");
        map.put("notes", null); // Company model doesn't have notes field
        return map;
    }
    
    /**
     * 将产品转换为 Map
     */
    private Map<String, Object> productToMap(Product product) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("name", product.getName());
        map.put("description", product.getDescription());
        map.put("category", product.getCategory());
        map.put("unit_price", product.getUnitPrice());
        map.put("unit", product.getUnit());
        map.put("tax_rate", product.getTaxRate());
        map.put("status", product.getStatus());
        map.put("notes", product.getNotes());
        return map;
    }
    
    /**
     * 规范化字符串（用于比较）
     */
    private String normalizeString(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase()
            .replaceAll("\\s+", " ")
            .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\s]", "");
    }
    
    /**
     * 计算字符串相似度（简化的 Levenshtein 距离）
     */
    private double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        // 检查包含关系
        if (s1.contains(s2) || s2.contains(s1)) {
            double shorter = Math.min(s1.length(), s2.length());
            double longer = Math.max(s1.length(), s2.length());
            return shorter / longer * 0.9; // 稍微降低包含关系的权重
        }
        
        // 简化的编辑距离计算
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }
    
    /**
     * 计算 Levenshtein 距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * 获取置信度级别
     */
    private String getConfidenceLevel(double score) {
        if (score >= 0.9) {
            return "非常高";
        } else if (score >= 0.8) {
            return "高";
        } else if (score >= 0.7) {
            return "中";
        } else if (score >= 0.6) {
            return "低";
        } else {
            return "很低";
        }
    }
}