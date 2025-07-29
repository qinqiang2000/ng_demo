package com.invoice.repository;

import com.invoice.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 产品信息数据访问接口
 * 
 * 提供产品信息的 CRUD 操作和查询功能
 * 支持智能查询和模糊匹配
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    /**
     * 根据产品名称查找产品
     * 
     * @param name 产品名称
     * @return 产品信息
     */
    Optional<Product> findByName(String name);

    /**
     * 根据产品编码查找产品
     * 
     * @param productCode 产品编码
     * @return 产品信息
     */
    Optional<Product> findByProductCode(String productCode);

    /**
     * 根据产品名称模糊查找产品（按名称排序）
     * 
     * @param name 产品名称关键词
     * @return 产品列表
     */
    List<Product> findByNameContainingIgnoreCaseOrderByName(String name);

    /**
     * 根据产品分类查找产品（按名称排序）
     * 
     * @param category 产品分类
     * @return 产品列表
     */
    List<Product> findByCategoryOrderByName(String category);

    /**
     * 根据产品名称和分类查找产品（按名称排序）
     * 
     * @param name 产品名称关键词
     * @param category 产品分类
     * @return 产品列表
     */
    List<Product> findByNameContainingIgnoreCaseAndCategoryOrderByName(String name, String category);

    /**
     * 根据产品名称或描述模糊查找产品（按名称排序）
     * 
     * @param name 名称关键词
     * @param description 描述关键词
     * @return 产品列表
     */
    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByName(String name, String description);

    /**
     * 查找所有产品（按名称排序）
     * 
     * @return 产品列表
     */
    List<Product> findAllByOrderByName();

    /**
     * 查找所有活跃产品
     * 
     * @return 活跃产品列表
     */
    List<Product> findByIsActiveTrueOrderByName();

    /**
     * 根据分类查找活跃产品
     * 
     * @param category 产品分类
     * @return 活跃产品列表
     */
    List<Product> findByCategoryAndIsActiveTrueOrderByName(String category);

    /**
     * 根据状态查找产品
     * 
     * @param status 产品状态
     * @return 产品列表
     */
    List<Product> findByStatusOrderByName(String status);

    /**
     * 根据品牌查找产品
     * 
     * @param brand 品牌
     * @return 产品列表
     */
    List<Product> findByBrandOrderByName(String brand);

    /**
     * 根据价格范围查找产品
     * 
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @return 产品列表
     */
    List<Product> findByUnitPriceBetweenOrderByUnitPriceAsc(BigDecimal minPrice, BigDecimal maxPrice);

    /**
     * 查找指定税率的产品
     * 
     * @param taxRate 税率
     * @return 产品列表
     */
    List<Product> findByTaxRateOrderByName(BigDecimal taxRate);

    /**
     * 检查产品名称是否存在
     * 
     * @param name 产品名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 检查产品编码是否存在
     * 
     * @param productCode 产品编码
     * @return 是否存在
     */
    boolean existsByProductCode(String productCode);

    /**
     * 根据关键词在多个字段中搜索产品
     * 
     * @param keyword 关键词
     * @return 产品列表
     */
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.specification) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND p.isActive = true " +
           "ORDER BY " +
           "CASE " +
           "  WHEN LOWER(p.name) = LOWER(:keyword) THEN 1 " +
           "  WHEN LOWER(p.name) LIKE LOWER(CONCAT(:keyword, '%')) THEN 2 " +
           "  WHEN LOWER(p.productCode) = LOWER(:keyword) THEN 3 " +
           "  ELSE 4 " +
           "END, p.name")
    List<Product> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 获取产品分类统计
     * 
     * @return 分类统计结果
     */
    @Query("SELECT p.category, COUNT(p) FROM Product p WHERE p.isActive = true GROUP BY p.category ORDER BY p.category")
    List<Object[]> getProductCategoryStats();

    /**
     * 获取价格区间统计
     * 
     * @return 价格区间统计
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN p.unitPrice < 100 THEN '0-100' " +
           "  WHEN p.unitPrice < 500 THEN '100-500' " +
           "  WHEN p.unitPrice < 1000 THEN '500-1000' " +
           "  WHEN p.unitPrice < 5000 THEN '1000-5000' " +
           "  ELSE '5000+' " +
           "END as priceRange, " +
           "COUNT(p) " +
           "FROM Product p WHERE p.isActive = true AND p.unitPrice IS NOT NULL " +
           "GROUP BY " +
           "CASE " +
           "  WHEN p.unitPrice < 100 THEN '0-100' " +
           "  WHEN p.unitPrice < 500 THEN '100-500' " +
           "  WHEN p.unitPrice < 1000 THEN '500-1000' " +
           "  WHEN p.unitPrice < 5000 THEN '1000-5000' " +
           "  ELSE '5000+' " +
           "END " +
           "ORDER BY " +
           "CASE " +
           "  WHEN p.unitPrice < 100 THEN 1 " +
           "  WHEN p.unitPrice < 500 THEN 2 " +
           "  WHEN p.unitPrice < 1000 THEN 3 " +
           "  WHEN p.unitPrice < 5000 THEN 4 " +
           "  ELSE 5 " +
           "END")
    List<Object[]> getPriceRangeStats();

    /**
     * 查找相似产品
     * 
     * 基于名称、分类和品牌的相似度匹配
     * 
     * @param name 产品名称
     * @param category 产品分类
     * @param brand 品牌
     * @return 相似产品列表
     */
    @Query("SELECT p FROM Product p WHERE " +
           "(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:brand IS NULL OR p.brand = :brand) AND " +
           "p.isActive = true " +
           "ORDER BY " +
           "CASE " +
           "  WHEN LOWER(p.name) = LOWER(:name) THEN 1 " +
           "  WHEN LOWER(p.name) LIKE LOWER(CONCAT(:name, '%')) THEN 2 " +
           "  ELSE 3 " +
           "END, p.name")
    List<Product> findSimilarProducts(@Param("name") String name, 
                                     @Param("category") String category, 
                                     @Param("brand") String brand);

    /**
     * 支持 CEL 表达式的智能查询
     * 
     * 模拟 Python 后端的 db.products.field[condition] 语法
     * 
     * @param fieldName 字段名
     * @param condition 查询条件
     * @return 查询结果
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN :fieldName = 'name' THEN p.name " +
           "  WHEN :fieldName = 'product_code' THEN p.productCode " +
           "  WHEN :fieldName = 'category' THEN p.category " +
           "  WHEN :fieldName = 'brand' THEN p.brand " +
           "  WHEN :fieldName = 'unit' THEN p.unit " +
           "  WHEN :fieldName = 'status' THEN p.status " +
           "  ELSE p.name " +
           "END " +
           "FROM Product p WHERE " +
           "(:fieldName = 'name' AND p.name = :condition) OR " +
           "(:fieldName = 'product_code' AND p.productCode = :condition) OR " +
           "(:fieldName = 'category' AND p.category = :condition) OR " +
           "(:fieldName = 'brand' AND p.brand = :condition) OR " +
           "(:fieldName = 'unit' AND p.unit = :condition) OR " +
           "(:fieldName = 'status' AND p.status = :condition) " +
           "AND p.isActive = true")
    List<String> findFieldValueByCondition(@Param("fieldName") String fieldName, 
                                         @Param("condition") String condition);

    /**
     * 查找推荐产品
     * 
     * 基于销量、评分等因素推荐产品
     * 
     * @param category 产品分类（可选）
     * @param limit 推荐数量限制
     * @return 推荐产品列表
     */
    @Query(value = "SELECT p.* FROM products p WHERE " +
                   "(:category IS NULL OR p.category = :category) AND " +
                   "p.is_active = true AND " +
                   "p.status = 'active' " +
                   "ORDER BY p.created_at DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<Product> findRecommendedProducts(@Param("category") String category, 
                                         @Param("limit") int limit);
}