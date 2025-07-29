package com.invoice.repository;

import com.invoice.models.BusinessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 业务规则配置数据访问接口
 * 
 * 与 Python 数据库访问功能完全等价
 * 提供业务规则配置的 CRUD 操作和查询功能
 */
@Repository
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, Integer> {

    /**
     * 根据规则ID查找业务规则
     * 
     * @param ruleId 规则ID
     * @return 业务规则
     */
    Optional<BusinessRule> findByRuleId(String ruleId);

    /**
     * 根据规则类型查找活跃规则
     * 
     * @param ruleType 规则类型（completion/validation）
     * @return 业务规则列表
     */
    List<BusinessRule> findByRuleTypeAndIsActiveTrueOrderByPriorityDescIdAsc(String ruleType);

    /**
     * 查找所有活跃的字段补全规则
     * 
     * @return 字段补全规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.ruleType = 'completion' AND r.isActive = true " +
           "ORDER BY r.priority DESC, r.id ASC")
    List<BusinessRule> findActiveCompletionRules();

    /**
     * 查找所有活跃的业务校验规则
     * 
     * @return 业务校验规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.ruleType = 'validation' AND r.isActive = true " +
           "ORDER BY r.priority DESC, r.id ASC")
    List<BusinessRule> findActiveValidationRules();

    /**
     * 根据目标字段查找补全规则
     * 
     * @param targetField 目标字段
     * @return 补全规则列表
     */
    List<BusinessRule> findByTargetFieldAndRuleTypeAndIsActiveTrue(String targetField, String ruleType);

    /**
     * 根据校验字段查找校验规则
     * 
     * @param fieldPath 校验字段路径
     * @return 校验规则列表
     */
    List<BusinessRule> findByFieldPathAndRuleTypeAndIsActiveTrue(String fieldPath, String ruleType);

    /**
     * 查找所有活跃规则并按优先级排序
     * 
     * @return 所有活跃规则
     */
    List<BusinessRule> findByIsActiveTrueOrderByPriorityDescIdAsc();

    /**
     * 根据优先级范围查找规则
     * 
     * @param minPriority 最小优先级
     * @param maxPriority 最大优先级
     * @return 规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.isActive = true AND " +
           "r.priority >= :minPriority AND r.priority <= :maxPriority " +
           "ORDER BY r.priority DESC, r.id ASC")
    List<BusinessRule> findByPriorityRange(@Param("minPriority") Integer minPriority,
                                          @Param("maxPriority") Integer maxPriority);

    /**
     * 查找高优先级规则
     * 
     * 优先级 >= 90 的规则
     * 
     * @return 高优先级规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.isActive = true AND r.priority >= 90 " +
           "ORDER BY r.priority DESC, r.id ASC")
    List<BusinessRule> findHighPriorityRules();

    /**
     * 检查规则ID是否存在
     * 
     * @param ruleId 规则ID
     * @return 是否存在
     */
    boolean existsByRuleId(String ruleId);

    /**
     * 统计各类型规则数量
     * 
     * @return 类型统计结果
     */
    @Query("SELECT r.ruleType, COUNT(r) FROM BusinessRule r WHERE r.isActive = true GROUP BY r.ruleType")
    List<Object[]> getRuleTypeStats();

    /**
     * 查找包含指定表达式的规则
     * 
     * @param expressionKeyword 表达式关键词
     * @return 规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.isActive = true AND " +
           "(r.ruleExpression LIKE %:keyword% OR r.applyTo LIKE %:keyword%)")
    List<BusinessRule> findRulesContainingExpression(@Param("keyword") String expressionKeyword);

    /**
     * 查找针对特定字段的所有规则
     * 
     * 包括补全规则和校验规则
     * 
     * @param fieldName 字段名称
     * @return 规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.isActive = true AND " +
           "(r.targetField LIKE %:fieldName% OR r.fieldPath LIKE %:fieldName%) " +
           "ORDER BY r.ruleType, r.priority DESC")
    List<BusinessRule> findRulesByField(@Param("fieldName") String fieldName);

    /**
     * 查找具有应用条件的规则
     * 
     * 只返回有 apply_to 条件的规则
     * 
     * @return 有条件的规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.isActive = true AND " +
           "r.applyTo IS NOT NULL AND r.applyTo != '' " +
           "ORDER BY r.priority DESC, r.id ASC")
    List<BusinessRule> findRulesWithConditions();

    /**
     * 查找无应用条件的规则
     * 
     * 返回总是应用的规则
     * 
     * @return 无条件的规则列表
     */
    @Query("SELECT r FROM BusinessRule r WHERE " +
           "r.isActive = true AND " +
           "(r.applyTo IS NULL OR r.applyTo = '') " +
           "ORDER BY r.priority DESC, r.id ASC")
    List<BusinessRule> findUnconditionalRules();

    /**
     * 根据规则名称模糊查询
     * 
     * @param nameKeyword 名称关键词
     * @return 规则列表
     */
    List<BusinessRule> findByRuleNameContainingIgnoreCaseAndIsActiveTrue(String nameKeyword);

    /**
     * 获取规则配置的完整性统计
     * 
     * @return 完整性统计
     */
    @Query("SELECT " +
           "SUM(CASE WHEN r.ruleType = 'completion' AND r.targetField IS NOT NULL THEN 1 ELSE 0 END) as validCompletionRules, " +
           "SUM(CASE WHEN r.ruleType = 'validation' AND r.fieldPath IS NOT NULL AND r.errorMessage IS NOT NULL THEN 1 ELSE 0 END) as validValidationRules, " +
           "SUM(CASE WHEN r.applyTo IS NOT NULL AND r.applyTo != '' THEN 1 ELSE 0 END) as rulesWithConditions, " +
           "COUNT(r) as totalActiveRules " +
           "FROM BusinessRule r WHERE r.isActive = true")
    Object[] getRuleIntegrityStats();
}