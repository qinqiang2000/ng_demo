package com.invoice.repository;

import com.invoice.models.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 企业信息数据访问接口
 * 
 * 与 Python 数据库访问功能完全等价
 * 提供企业信息的 CRUD 操作和查询功能
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Integer> {

    /**
     * 根据税号查找企业
     * 
     * @param taxNumber 税号
     * @return 企业信息
     */
    Optional<Company> findByTaxNumber(String taxNumber);

    /**
     * 根据企业名称查找企业
     * 
     * @param name 企业名称
     * @return 企业信息
     */
    Optional<Company> findByName(String name);

    /**
     * 根据企业名称模糊查找企业
     * 
     * @param name 企业名称关键词
     * @return 企业列表
     */
    List<Company> findByNameContainingIgnoreCase(String name);

    /**
     * 根据企业名称模糊查找企业（按名称排序）
     * 
     * @param name 企业名称关键词
     * @return 企业列表
     */
    List<Company> findByNameContainingIgnoreCaseOrderByName(String name);

    /**
     * 根据企业分类查找企业
     * 
     * @param category 企业分类
     * @return 企业列表
     */
    List<Company> findByCategory(String category);

    /**
     * 根据企业分类查找企业（按名称排序）
     * 
     * @param category 企业分类
     * @return 企业列表
     */
    List<Company> findByCategoryOrderByName(String category);

    /**
     * 根据企业名称和分类查找企业（按名称排序）
     * 
     * @param name 企业名称关键词
     * @param category 企业分类
     * @return 企业列表
     */
    List<Company> findByNameContainingIgnoreCaseAndCategoryOrderByName(String name, String category);

    /**
     * 查找所有企业（按名称排序）
     * 
     * @return 企业列表
     */
    List<Company> findAllByOrderByName();

    /**
     * 查找所有活跃企业
     * 
     * @return 活跃企业列表
     */
    List<Company> findByIsActiveTrue();

    /**
     * 根据分类查找活跃企业
     * 
     * @param category 企业分类
     * @return 活跃企业列表
     */
    List<Company> findByCategoryAndIsActiveTrue(String category);

    /**
     * 检查税号是否存在
     * 
     * @param taxNumber 税号
     * @return 是否存在
     */
    boolean existsByTaxNumber(String taxNumber);

    /**
     * 检查企业名称是否存在
     * 
     * @param name 企业名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据联系方式查找企业
     * 
     * @param phone 电话
     * @param email 邮箱
     * @return 企业列表
     */
    @Query("SELECT c FROM Company c WHERE c.phone = :phone OR c.email = :email")
    List<Company> findByPhoneOrEmail(@Param("phone") String phone, @Param("email") String email);

    /**
     * 查找有税号的企业
     * 
     * @return 有税号的企业列表
     */
    @Query("SELECT c FROM Company c WHERE c.taxNumber IS NOT NULL AND c.taxNumber != ''")
    List<Company> findCompaniesWithTaxNumber();

    /**
     * 根据地址关键词查找企业
     * 
     * @param addressKeyword 地址关键词
     * @return 企业列表
     */
    @Query("SELECT c FROM Company c WHERE c.address LIKE %:keyword%")
    List<Company> findByAddressContaining(@Param("keyword") String addressKeyword);

    /**
     * 获取企业分类统计
     * 
     * @return 分类统计结果
     */
    @Query("SELECT c.category, COUNT(c) FROM Company c WHERE c.isActive = true GROUP BY c.category")
    List<Object[]> getCompanyCategoryStats();

    /**
     * 查找标准名称匹配的企业
     * 
     * 用于支持智能查询功能
     * 
     * @param standardName 标准名称
     * @return 企业信息
     */
    @Query("SELECT c FROM Company c WHERE " +
           "LOWER(c.name) = LOWER(:standardName) OR " +
           "c.name LIKE %:standardName% " +
           "AND c.isActive = true " +
           "ORDER BY " +
           "CASE " +
           "  WHEN LOWER(c.name) = LOWER(:standardName) THEN 1 " +
           "  WHEN c.name LIKE :standardName% THEN 2 " +
           "  ELSE 3 " +
           "END")
    List<Company> findByStandardName(@Param("standardName") String standardName);

    /**
     * 支持 CEL 表达式的智能查询
     * 
     * 根据公司名称查找指定字段的值
     * 
     * @param fieldName 要查询的字段名
     * @param companyName 公司名称
     * @return 查询结果
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN :fieldName = 'name' THEN c.name " +
           "  WHEN :fieldName = 'tax_number' THEN c.taxNumber " +
           "  WHEN :fieldName = 'phone' THEN c.phone " +
           "  WHEN :fieldName = 'email' THEN c.email " +
           "  WHEN :fieldName = 'address' THEN c.address " +
           "  WHEN :fieldName = 'category' THEN c.category " +
           "  ELSE c.name " +
           "END " +
           "FROM Company c WHERE " +
           "c.name = :companyName " +
           "AND c.isActive = true")
    List<String> findFieldValueByCondition(@Param("fieldName") String fieldName, 
                                          @Param("companyName") String companyName);
}