package com.invoice.spel.services;

import com.invoice.models.Company;
import com.invoice.models.Product;
import com.invoice.models.TaxRate;
import com.invoice.repository.CompanyRepository;
import com.invoice.repository.ProductRepository;
import com.invoice.repository.TaxRateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * SpEL 数据库服务
 * 
 * 复用现有的 Repository 层，为 SpEL 表达式提供数据库查询功能
 * 支持公司信息、产品信息、税率等数据查询
 */
@Service
@Slf4j
public class DbService {
    
    @Autowired
    private CompanyRepository companyRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private TaxRateRepository taxRateRepository;
    
    /**
     * 根据公司名称获取税号
     */
    public String getCompanyTaxNumber(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return null;
        }
        
        log.debug("查询公司税号: {}", companyName);
        Optional<Company> company = companyRepository.findByName(companyName.trim());
        String taxNumber = company.map(Company::getTaxNumber).orElse(null);
        log.debug("公司 {} 的税号: {}", companyName, taxNumber);
        return taxNumber;
    }
    
    /**
     * 根据公司名称获取邮箱
     */
    public String getCompanyEmail(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return null;
        }
        
        log.debug("查询公司邮箱: {}", companyName);
        Optional<Company> company = companyRepository.findByName(companyName.trim());
        String email = company.map(Company::getEmail).orElse(null);
        log.debug("公司 {} 的邮箱: {}", companyName, email);
        return email;
    }
    
    /**
     * 根据公司名称获取分类
     */
    public String getCompanyCategory(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return null;
        }
        
        log.debug("查询公司分类: {}", companyName);
        Optional<Company> company = companyRepository.findByName(companyName.trim());
        String category = company.map(Company::getCategory).orElse(null);
        log.debug("公司 {} 的分类: {}", companyName, category);
        return category;
    }
    
    /**
     * 根据税收分类获取税率
     */
    public BigDecimal getTaxRate(String taxCategory) {
        if (taxCategory == null || taxCategory.trim().isEmpty()) {
            return null;
        }
        
        log.debug("查询税率: {}", taxCategory);
        Optional<BigDecimal> rate = taxRateRepository.getRecommendedTaxRate(taxCategory.trim());
        BigDecimal result = rate.orElse(null);
        log.debug("税收分类 {} 的税率: {}", taxCategory, result);
        return result;
    }
    
    /**
     * 根据产品名称获取标准名称
     */
    public String getProductStandardName(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return null;
        }
        
        log.debug("查询产品标准名称: {}", productName);
        Optional<Product> product = productRepository.findByName(productName.trim());
        // Product 模型中没有 standardName 字段，使用 name 字段作为标准名称
        String standardName = product.map(Product::getName).orElse(null);
        log.debug("产品 {} 的标准名称: {}", productName, standardName);
        return standardName;
    }
    
    /**
     * 根据产品名称获取税收分类
     */
    public String getProductTaxCategory(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return null;
        }
        
        log.debug("查询产品税收分类: {}", productName);
        Optional<Product> product = productRepository.findByName(productName.trim());
        // Product 模型中使用 category 字段作为税收分类
        String taxCategory = product.map(Product::getCategory).orElse(null);
        log.debug("产品 {} 的税收分类: {}", productName, taxCategory);
        return taxCategory;
    }
    
    /**
     * 通用字段查询方法
     * 
     * @param table 表名
     * @param field 字段名
     * @param conditionField 条件字段名
     * @param conditionValue 条件值
     * @return 查询结果
     */
    public Object queryField(String table, String field, String conditionField, Object conditionValue) {
        log.debug("通用查询: table={}, field={}, condition={}={}", 
            table, field, conditionField, conditionValue);
        
        switch (table) {
            case "companies":
                if ("name".equals(conditionField) && conditionValue != null) {
                    String companyName = conditionValue.toString();
                    switch (field) {
                        case "tax_number":
                            return getCompanyTaxNumber(companyName);
                        case "email":
                            return getCompanyEmail(companyName);
                        case "category":
                            return getCompanyCategory(companyName);
                    }
                }
                break;
                
            case "tax_rates":
                if ("category".equals(conditionField) && "rate".equals(field) && conditionValue != null) {
                    return getTaxRate(conditionValue.toString());
                }
                break;
                
                case "products":
                if ("name".equals(conditionField) && conditionValue != null) {
                    String productName = conditionValue.toString();
                    switch (field) {
                        case "standard_name":
                            return getProductStandardName(productName);
                        case "tax_category":
                            return getProductTaxCategory(productName);
                    }
                }
                break;
        }
        
        log.warn("不支持的查询: table={}, field={}, condition={}={}", 
            table, field, conditionField, conditionValue);
        return null;
    }
}