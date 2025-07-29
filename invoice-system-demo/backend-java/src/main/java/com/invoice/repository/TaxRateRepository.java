package com.invoice.repository;

import com.invoice.models.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 税率配置数据访问接口
 * 
 * 与 Python 数据库访问功能完全等价
 * 提供税率配置的 CRUD 操作和查询功能
 */
@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, Integer> {

    /**
     * 根据税率名称查找税率配置
     * 
     * @param name 税率名称
     * @return 税率配置
     */
    Optional<TaxRate> findByName(String name);

    /**
     * 根据分类查找活跃税率配置
     * 
     * @param category 分类
     * @return 税率配置列表
     */
    List<TaxRate> findByCategoryAndIsActiveTrue(String category);

    /**
     * 查找所有活跃税率配置
     * 
     * @return 活跃税率配置列表
     */
    List<TaxRate> findByIsActiveTrueOrderByRateAsc();

    /**
     * 根据税率值查找税率配置
     * 
     * @param rate 税率值
     * @return 税率配置列表
     */
    List<TaxRate> findByRateAndIsActiveTrue(BigDecimal rate);

    /**
     * 查找适用于指定金额的税率配置
     * 
     * @param amount 金额
     * @param category 分类
     * @return 税率配置列表
     */
    @Query("SELECT t FROM TaxRate t WHERE " +
           "t.isActive = true AND " +
           "(:category IS NULL OR t.category IS NULL OR t.category = :category) AND " +
           "(t.minAmount IS NULL OR t.minAmount <= :amount) AND " +
           "(t.maxAmount IS NULL OR t.maxAmount >= :amount) " +
           "ORDER BY t.rate DESC")
    List<TaxRate> findApplicableTaxRates(@Param("amount") BigDecimal amount, 
                                        @Param("category") String category);

    /**
     * 查找标准税率
     * 
     * 标准税率通常为 6% 或 13%
     * 
     * @return 标准税率列表
     */
    @Query("SELECT t FROM TaxRate t WHERE " +
           "t.isActive = true AND " +
           "(t.rate = 0.06 OR t.rate = 0.13) " +
           "ORDER BY t.rate ASC")
    List<TaxRate> findStandardTaxRates();

    /**
     * 查找小规模纳税人税率
     * 
     * 小规模纳税人税率通常为 3%
     * 
     * @return 小规模纳税人税率列表
     */
    @Query("SELECT t FROM TaxRate t WHERE " +
           "t.isActive = true AND " +
           "t.rate = 0.03")
    List<TaxRate> findSmallScaleTaxRates();

    /**
     * 查找零税率配置
     * 
     * @return 零税率配置列表
     */
    @Query("SELECT t FROM TaxRate t WHERE " +
           "t.isActive = true AND " +
           "t.rate = 0.0")
    List<TaxRate> findZeroTaxRates();

    /**
     * 根据分类统计税率配置数量
     * 
     * @return 分类统计结果
     */
    @Query("SELECT t.category, COUNT(t) FROM TaxRate t WHERE t.isActive = true GROUP BY t.category")
    List<Object[]> getTaxRateCategoryStats();

    /**
     * 查找指定范围内的税率
     * 
     * @param minRate 最小税率
     * @param maxRate 最大税率
     * @return 税率列表
     */
    @Query("SELECT t FROM TaxRate t WHERE " +
           "t.isActive = true AND " +
           "t.rate >= :minRate AND t.rate <= :maxRate " +
           "ORDER BY t.rate ASC")
    List<TaxRate> findTaxRatesInRange(@Param("minRate") BigDecimal minRate, 
                                     @Param("maxRate") BigDecimal maxRate);

    /**
     * 支持 CEL 表达式的智能查询
     * 
     * 模拟 Python 后端的 db.tax_rates.field[condition] 语法
     * 
     * @param fieldName 字段名
     * @param condition 查询条件
     * @return 查询结果
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN :fieldName = 'rate' THEN CAST(t.rate AS string) " +
           "  WHEN :fieldName = 'name' THEN t.name " +
           "  WHEN :fieldName = 'category' THEN t.category " +
           "  WHEN :fieldName = 'description' THEN t.description " +
           "  ELSE t.name " +
           "END " +
           "FROM TaxRate t WHERE t.isActive = true AND " +
           "(:condition IS NULL OR " +
           " (:fieldName = 'rate' AND CAST(t.rate AS string) LIKE CONCAT('%', :condition, '%')) OR " +
           " (:fieldName = 'name' AND t.name LIKE CONCAT('%', :condition, '%')) OR " +
           " (:fieldName = 'category' AND t.category LIKE CONCAT('%', :condition, '%')) OR " +
           " (:fieldName = 'description' AND t.description LIKE CONCAT('%', :condition, '%')))")
    List<String> findFieldValuesByCondition(@Param("fieldName") String fieldName, 
                                           @Param("condition") String condition);

    /**
     * 根据类别获取推荐税率
     * 
     * 用于 CEL 自定义函数 get_tax_rate
     * 
     * @param category 企业类别
     * @return 推荐税率
     */
    @Query("SELECT t.rate FROM TaxRate t WHERE " +
           "t.isActive = true AND " +
           "(:category IS NULL OR t.category = :category OR t.category IS NULL) " +
           "ORDER BY " +
           "CASE WHEN t.category = :category THEN 1 ELSE 2 END, " +
           "t.rate DESC " +
           "LIMIT 1")
    Optional<BigDecimal> getRecommendedTaxRate(@Param("category") String category);

    /**
     * 根据金额和类别获取最佳税率
     * 
     * @param amount 金额
     * @param category 类别
     * @return 最佳税率
     */
    @Query("SELECT t FROM TaxRate t WHERE " +
           "t.isActive = true AND " +
           "(:category IS NULL OR t.category = :category OR t.category IS NULL) AND " +
           "(t.minAmount IS NULL OR t.minAmount <= :amount) AND " +
           "(t.maxAmount IS NULL OR t.maxAmount >= :amount) " +
           "ORDER BY " +
           "CASE WHEN t.category = :category THEN 1 ELSE 2 END, " +
           "t.rate DESC " +
           "LIMIT 1")
    Optional<TaxRate> findBestTaxRate(@Param("amount") BigDecimal amount, 
                                     @Param("category") String category);

    /**
     * 检查指定税率是否存在
     * 
     * @param rate 税率值
     * @param category 类别
     * @return 是否存在
     */
    boolean existsByRateAndCategoryAndIsActiveTrue(BigDecimal rate, String category);
}