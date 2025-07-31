package com.invoice.controllers;

import com.invoice.core.RuleEngine;
import com.invoice.spel.SpelRuleEngine;
import com.invoice.config.RuleEngineConfigService;
import com.invoice.models.BusinessRule;
import com.invoice.repository.BusinessRuleRepository;
import com.invoice.dto.RuleRequest;
import com.invoice.dto.ExpressionValidationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 规则管理控制器
 * 
 * 提供规则配置的API接口
 * 与 Python 版本功能完全等价
 */
@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@RequiredArgsConstructor
@Slf4j
@Validated
public class RulesController {

    private final RuleEngine ruleEngine;
    private final SpelRuleEngine spelRuleEngine;
    private final RuleEngineConfigService ruleEngineConfigService;
    private final BusinessRuleRepository businessRuleRepository;

    /**
     * 获取所有规则
     * 
     * @return 规则列表和统计信息
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRules() {
        try {
            log.info("获取规则列表，当前引擎: {}", ruleEngineConfigService.getCurrentEngine());
            
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> completionRules = new ArrayList<>();
            List<Map<String, Object>> validationRules = new ArrayList<>();
            Map<String, Object> stats;
            
            // 根据当前引擎类型获取规则
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 使用 SpEL 引擎 - 不重新加载规则，使用内存中的规则
                // spelRuleEngine.loadRules("../shared/config/rules_spel.yaml"); // 注释掉，避免覆盖内存中的更改
                stats = spelRuleEngine.getRuleStats();
                
                // 获取 SpEL 规则并分类
                List<BusinessRule> spelCompletionRules = spelRuleEngine.getCompletionRules();
                List<BusinessRule> spelValidationRules = spelRuleEngine.getValidationRules();
                
                completionRules = spelCompletionRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
                validationRules = spelValidationRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
            } else {
                // 使用 CEL 引擎
                ruleEngine.loadRules("../shared/config/rules.yaml");
                stats = ruleEngine.getRuleStats();
                
                // 获取 CEL 规则并分类
                List<RuleEngine.CompletionRule> celCompletionRules = ruleEngine.getCompletionRules();
                List<RuleEngine.ValidationRule> celValidationRules = ruleEngine.getValidationRules();
                
                completionRules = celCompletionRules.stream()
                    .map(this::convertCompletionRuleToMap)
                    .collect(Collectors.toList());
                validationRules = celValidationRules.stream()
                    .map(this::convertValidationRuleToMap)
                    .collect(Collectors.toList());
            }
            
            // 构建前端期望的数据结构
            Map<String, Object> data = new HashMap<>();
            data.put("completion_rules", completionRules);
            data.put("validation_rules", validationRules);
            data.put("totalRules", stats.get("rules_loaded"));
            data.put("completionRulesCount", stats.get("completion_rules_count"));
            data.put("validationRulesCount", stats.get("validation_rules_count"));
            data.put("activeRules", stats.get("active_rules"));
            data.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            // 返回前端期望的响应格式
            response.put("success", true);
            response.put("data", data);
            response.put("message", "规则列表获取成功");
            
            log.info("规则列表获取完成 - 引擎: {}, 总数: {}, 补全: {}, 验证: {}", 
                ruleEngineConfigService.getCurrentEngine(),
                stats.get("rules_loaded"), 
                stats.get("completion_rules_count"), 
                stats.get("validation_rules_count"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "获取规则列表失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 部分更新补全规则
     * 
     * @param ruleId 规则ID
     * @param updates 部分更新数据
     * @return 更新结果
     */
    @PatchMapping("/completion/{ruleId}")
    public ResponseEntity<Map<String, Object>> patchCompletionRule(@PathVariable String ruleId, @RequestBody Map<String, Object> updates) {
        try {
            log.info("部分更新补全规则，规则ID: {}", ruleId);
            
            // 确保规则已加载
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 不重新加载规则，使用内存中的规则
                // spelRuleEngine.loadRules("../shared/config/rules_spel.yaml");
                
                // 查找规则
                BusinessRule rule = spelRuleEngine.findRuleById(ruleId);
                if (rule == null) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "规则不存在");
                    errorResponse.put("message", "未找到指定的补全规则");
                    return ResponseEntity.status(404).body(errorResponse);
                }
                
                if (!"completion".equals(rule.getRuleType())) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "规则类型不匹配");
                    errorResponse.put("message", "指定规则不是补全规则");
                    return ResponseEntity.status(400).body(errorResponse);
                }
                
                // 更新规则
                boolean success = spelRuleEngine.updateRule(ruleId, updates);
                if (!success) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "更新失败");
                    errorResponse.put("message", "规则更新失败");
                    return ResponseEntity.status(500).body(errorResponse);
                }
                
                // 获取更新后的规则
                BusinessRule updatedRule = spelRuleEngine.findRuleById(ruleId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", convertBusinessRuleToMap(updatedRule));
                response.put("message", "补全规则部分更新成功");
                
                log.info("补全规则部分更新完成，规则ID: {}", ruleId);
                return ResponseEntity.ok(response);
                
            } else {
                // CEL 引擎暂不支持动态更新
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "当前引擎不支持");
                errorResponse.put("message", "CEL 引擎暂不支持动态规则更新");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
        } catch (Exception e) {
            log.error("部分更新补全规则失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "部分更新补全规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 部分更新校验规则
     * 
     * @param ruleId 规则ID
     * @param updates 部分更新数据
     * @return 更新结果
     */
    @PatchMapping("/validation/{ruleId}")
    public ResponseEntity<Map<String, Object>> patchValidationRule(@PathVariable String ruleId, @RequestBody Map<String, Object> updates) {
        try {
            log.info("部分更新校验规则，规则ID: {}", ruleId);
            
            // 确保规则已加载
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 不重新加载规则，使用内存中的规则
                // spelRuleEngine.loadRules("../shared/config/rules_spel.yaml");
                
                // 查找规则
                BusinessRule rule = spelRuleEngine.findRuleById(ruleId);
                if (rule == null) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "规则不存在");
                    errorResponse.put("message", "未找到指定的校验规则");
                    return ResponseEntity.status(404).body(errorResponse);
                }
                
                if (!"validation".equals(rule.getRuleType())) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "规则类型不匹配");
                    errorResponse.put("message", "指定规则不是校验规则");
                    return ResponseEntity.status(400).body(errorResponse);
                }
                
                // 更新规则
                boolean success = spelRuleEngine.updateRule(ruleId, updates);
                if (!success) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "更新失败");
                    errorResponse.put("message", "规则更新失败");
                    return ResponseEntity.status(500).body(errorResponse);
                }
                
                // 获取更新后的规则
                BusinessRule updatedRule = spelRuleEngine.findRuleById(ruleId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", convertBusinessRuleToMap(updatedRule));
                response.put("message", "校验规则部分更新成功");
                
                log.info("校验规则部分更新完成，规则ID: {}", ruleId);
                return ResponseEntity.ok(response);
                
            } else {
                // CEL 引擎暂不支持动态更新
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "当前引擎不支持");
                errorResponse.put("message", "CEL 引擎暂不支持动态规则更新");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
        } catch (Exception e) {
            log.error("部分更新校验规则失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "部分更新校验规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 更新补全规则
     * 
     * @param ruleId 规则ID
     * @param request 规则更新请求
     * @return 更新结果
     */
    @PutMapping("/completion/{ruleId}")
    public ResponseEntity<Map<String, Object>> updateCompletionRule(@PathVariable String ruleId, @Valid @RequestBody RuleRequest request) {
        try {
            log.info("更新补全规则，规则ID: {}", ruleId);
            
            // 验证规则类型
            if (!"completion".equals(request.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "更新补全规则时规则类型必须为 completion");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            // 查找现有规则
            Optional<BusinessRule> ruleOpt = businessRuleRepository.findByRuleId(ruleId);
            if (ruleOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则不存在");
                errorResponse.put("message", "未找到指定的补全规则");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            BusinessRule rule = ruleOpt.get();
            if (!"completion".equals(rule.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "指定规则不是补全规则");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            // 更新规则字段
            rule.setRuleName(request.getRuleName());
            rule.setApplyTo(request.getApplyTo());
            rule.setTargetField(request.getTargetField());
            rule.setRuleExpression(request.getRuleExpression());
            rule.setErrorMessage(request.getErrorMessage());
            rule.setPriority(request.getPriority() != null ? request.getPriority() : rule.getPriority());
            rule.setIsActive(request.getActive() != null ? request.getActive() : rule.getIsActive());
            
            BusinessRule savedRule = businessRuleRepository.save(rule);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", convertBusinessRuleToMap(savedRule));
            response.put("message", "补全规则更新成功");
            
            log.info("补全规则更新完成，规则ID: {}", ruleId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("更新补全规则失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "更新补全规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 更新校验规则
     * 
     * @param ruleId 规则ID
     * @param request 规则更新请求
     * @return 更新结果
     */
    @PutMapping("/validation/{ruleId}")
    public ResponseEntity<Map<String, Object>> updateValidationRule(@PathVariable String ruleId, @Valid @RequestBody RuleRequest request) {
        try {
            log.info("更新校验规则，规则ID: {}", ruleId);
            
            // 验证规则类型
            if (!"validation".equals(request.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "更新校验规则时规则类型必须为 validation");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            // 查找现有规则
            Optional<BusinessRule> ruleOpt = businessRuleRepository.findByRuleId(ruleId);
            if (ruleOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则不存在");
                errorResponse.put("message", "未找到指定的校验规则");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            BusinessRule rule = ruleOpt.get();
            if (!"validation".equals(rule.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "指定规则不是校验规则");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            // 更新规则字段
            rule.setRuleName(request.getRuleName());
            rule.setApplyTo(request.getApplyTo());
            rule.setFieldPath(request.getFieldPath());
            rule.setRuleExpression(request.getRuleExpression());
            rule.setErrorMessage(request.getErrorMessage());
            rule.setPriority(request.getPriority() != null ? request.getPriority() : rule.getPriority());
            rule.setIsActive(request.getActive() != null ? request.getActive() : rule.getIsActive());
            
            BusinessRule savedRule = businessRuleRepository.save(rule);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", convertBusinessRuleToMap(savedRule));
            response.put("message", "校验规则更新成功");
            
            log.info("校验规则更新完成，规则ID: {}", ruleId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("更新校验规则失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "更新校验规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 创建补全规则
     * 
     * @param request 规则创建请求
     * @return 创建结果
     */
    @PostMapping("/completion")
    public ResponseEntity<Map<String, Object>> createCompletionRule(@Valid @RequestBody RuleRequest request) {
        try {
            log.info("创建补全规则，规则名称: {}", request.getRuleName());
            
            // 验证规则类型
            if (!"completion".equals(request.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "创建补全规则时规则类型必须为 completion");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            // 检查规则ID是否已存在
            if (request.getRuleId() != null && businessRuleRepository.findByRuleId(request.getRuleId()).isPresent()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则ID已存在");
                errorResponse.put("message", "指定的规则ID已被使用");
                return ResponseEntity.status(409).body(errorResponse);
            }
            
            // 创建新规则
            BusinessRule rule = new BusinessRule();
            rule.setRuleId(request.getRuleId() != null ? request.getRuleId() : UUID.randomUUID().toString());
            rule.setRuleName(request.getRuleName());
            rule.setRuleType("completion");
            rule.setApplyTo(request.getApplyTo());
            rule.setTargetField(request.getTargetField());
            rule.setRuleExpression(request.getRuleExpression());
            rule.setErrorMessage(request.getErrorMessage());
            rule.setPriority(request.getPriority() != null ? request.getPriority() : 100);
            rule.setIsActive(request.getActive() != null ? request.getActive() : true);
            
            BusinessRule savedRule = businessRuleRepository.save(rule);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", convertBusinessRuleToMap(savedRule));
            response.put("message", "补全规则创建成功");
            
            log.info("补全规则创建完成，规则ID: {}", savedRule.getRuleId());
            return ResponseEntity.status(201).body(response);
            
        } catch (Exception e) {
            log.error("创建补全规则失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "创建补全规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 创建校验规则
     * 
     * @param request 规则创建请求
     * @return 创建结果
     */
    @PostMapping("/validation")
    public ResponseEntity<Map<String, Object>> createValidationRule(@Valid @RequestBody RuleRequest request) {
        try {
            log.info("创建校验规则，规则名称: {}", request.getRuleName());
            
            // 验证规则类型
            if (!"validation".equals(request.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "创建校验规则时规则类型必须为 validation");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            // 检查规则ID是否已存在
            if (request.getRuleId() != null && businessRuleRepository.findByRuleId(request.getRuleId()).isPresent()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则ID已存在");
                errorResponse.put("message", "指定的规则ID已被使用");
                return ResponseEntity.status(409).body(errorResponse);
            }
            
            // 创建新规则
            BusinessRule rule = new BusinessRule();
            rule.setRuleId(request.getRuleId() != null ? request.getRuleId() : UUID.randomUUID().toString());
            rule.setRuleName(request.getRuleName());
            rule.setRuleType("validation");
            rule.setApplyTo(request.getApplyTo());
            rule.setFieldPath(request.getFieldPath());
            rule.setRuleExpression(request.getRuleExpression());
            rule.setErrorMessage(request.getErrorMessage());
            rule.setPriority(request.getPriority() != null ? request.getPriority() : 100);
            rule.setIsActive(request.getActive() != null ? request.getActive() : true);
            
            BusinessRule savedRule = businessRuleRepository.save(rule);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", convertBusinessRuleToMap(savedRule));
            response.put("message", "校验规则创建成功");
            
            log.info("校验规则创建完成，规则ID: {}", savedRule.getRuleId());
            return ResponseEntity.status(201).body(response);
            
        } catch (Exception e) {
            log.error("创建校验规则失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "创建校验规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 将 BusinessRule 转换为 Map 格式
     */
    private Map<String, Object> convertBusinessRuleToMap(BusinessRule rule) {
        Map<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("id", rule.getRuleId());
        ruleMap.put("rule_name", rule.getRuleName());
        ruleMap.put("rule_type", rule.getRuleType());
        ruleMap.put("apply_to", rule.getApplyTo());
        ruleMap.put("target_field", rule.getTargetField());
        ruleMap.put("field_path", rule.getFieldPath());
        ruleMap.put("rule_expression", rule.getRuleExpression());
        ruleMap.put("error_message", rule.getErrorMessage());
        ruleMap.put("priority", rule.getPriority());
        ruleMap.put("active", rule.getIsActive());
        return ruleMap;
    }
    
    /**
     * 将 CompletionRule 转换为 Map 格式
     */
    private Map<String, Object> convertCompletionRuleToMap(RuleEngine.CompletionRule rule) {
        Map<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("id", rule.getId());
        ruleMap.put("rule_name", rule.getRuleName());
        ruleMap.put("rule_type", "completion");
        ruleMap.put("apply_to", rule.getApplyTo());
        ruleMap.put("target_field", rule.getTargetField());
        ruleMap.put("rule_expression", rule.getRuleExpression());
        ruleMap.put("priority", rule.getPriority());
        ruleMap.put("active", rule.isActive());
        return ruleMap;
    }
    
    /**
     * 将 ValidationRule 转换为 Map 格式
     */
    private Map<String, Object> convertValidationRuleToMap(RuleEngine.ValidationRule rule) {
        Map<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("id", rule.getId());
        ruleMap.put("rule_name", rule.getRuleName());
        ruleMap.put("rule_type", "validation");
        ruleMap.put("apply_to", rule.getApplyTo());
        ruleMap.put("field_path", rule.getFieldPath());
        ruleMap.put("rule_expression", rule.getRuleExpression());
        ruleMap.put("error_message", rule.getErrorMessage());
        ruleMap.put("priority", rule.getPriority());
        ruleMap.put("active", rule.isActive());
        return ruleMap;
    }
    
    /**
     * 获取补全规则
     * 
     * @return 补全规则列表
     */
    @GetMapping("/completion")
    public ResponseEntity<Map<String, Object>> getCompletionRules() {
        try {
            log.info("获取补全规则列表，当前引擎: {}", ruleEngineConfigService.getCurrentEngine());
            
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> completionRules = new ArrayList<>();
            Map<String, Object> ruleStats;
            
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 使用 SpEL 引擎
                spelRuleEngine.loadRules("../shared/config/rules_spel.yaml");
                ruleStats = spelRuleEngine.getRuleStats();
                
                List<BusinessRule> spelCompletionRules = spelRuleEngine.getCompletionRules();
                completionRules = spelCompletionRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
            } else {
                // 使用 CEL 引擎
                ruleEngine.loadRules("../shared/config/rules.yaml");
                ruleStats = ruleEngine.getRuleStats();
                
                List<RuleEngine.CompletionRule> celCompletionRules = ruleEngine.getCompletionRules();
                completionRules = celCompletionRules.stream()
                    .map(this::convertCompletionRuleToMap)
                    .collect(Collectors.toList());
            }
            
            response.put("rules", completionRules);
            response.put("message", "补全规则列表");
            response.put("totalRules", ruleStats.get("completion_rules_count"));
            response.put("activeRules", ruleStats.get("active_completion_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            response.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            log.info("补全规则列表获取完成 - 引擎: {}, 总数: {}, 活跃: {}", 
                ruleEngineConfigService.getCurrentEngine(),
                ruleStats.get("completion_rules_count"), ruleStats.get("active_completion_rules"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取补全规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取补全规则列表失败: " + e.getMessage());
            errorResponse.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取验证规则
     * 
     * @return 验证规则列表
     */
    @GetMapping("/validation")
    public ResponseEntity<Map<String, Object>> getValidationRules() {
        try {
            log.info("获取验证规则列表，当前引擎: {}", ruleEngineConfigService.getCurrentEngine());
            
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> validationRules = new ArrayList<>();
            Map<String, Object> ruleStats;
            
            if (ruleEngineConfigService.isCurrentlySpel()) {
                // 使用 SpEL 引擎
                spelRuleEngine.loadRules("../shared/config/rules_spel.yaml");
                ruleStats = spelRuleEngine.getRuleStats();
                
                List<BusinessRule> spelValidationRules = spelRuleEngine.getValidationRules();
                validationRules = spelValidationRules.stream()
                    .map(this::convertBusinessRuleToMap)
                    .collect(Collectors.toList());
            } else {
                // 使用 CEL 引擎
                ruleEngine.loadRules("../shared/config/rules.yaml");
                ruleStats = ruleEngine.getRuleStats();
                
                List<RuleEngine.ValidationRule> celValidationRules = ruleEngine.getValidationRules();
                validationRules = celValidationRules.stream()
                    .map(this::convertValidationRuleToMap)
                    .collect(Collectors.toList());
            }
            
            response.put("rules", validationRules);
            response.put("message", "验证规则列表");
            response.put("totalRules", ruleStats.get("validation_rules_count"));
            response.put("activeRules", ruleStats.get("active_validation_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            response.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            log.info("验证规则列表获取完成 - 引擎: {}, 总数: {}, 活跃: {}", 
                ruleEngineConfigService.getCurrentEngine(),
                ruleStats.get("validation_rules_count"), ruleStats.get("active_validation_rules"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取验证规则列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取验证规则列表失败: " + e.getMessage());
            errorResponse.put("engine", ruleEngineConfigService.getCurrentEngine());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取域字段列表
     * 
     * @return 域字段列表
     */
    @GetMapping("/domain-fields")
    public ResponseEntity<Map<String, Object>> getDomainFields() {
        try {
            log.info("获取域字段列表");
            
            Map<String, Object> fields = new HashMap<>();
            
            // 发票基本字段
            List<Map<String, Object>> invoiceFields = new ArrayList<>();
            invoiceFields.add(Map.of("name", "invoiceNumber", "type", "String", "description", "发票号码"));
            invoiceFields.add(Map.of("name", "issueDate", "type", "LocalDate", "description", "开票日期"));
            invoiceFields.add(Map.of("name", "totalAmount", "type", "BigDecimal", "description", "总金额"));
            invoiceFields.add(Map.of("name", "taxAmount", "type", "BigDecimal", "description", "税额"));
            invoiceFields.add(Map.of("name", "currency", "type", "String", "description", "币种"));
            
            // 供应商字段
            List<Map<String, Object>> supplierFields = new ArrayList<>();
            supplierFields.add(Map.of("name", "supplier.name", "type", "String", "description", "供应商名称"));
            supplierFields.add(Map.of("name", "supplier.taxNo", "type", "String", "description", "供应商税号"));
            supplierFields.add(Map.of("name", "supplier.legalRepresentative", "type", "String", "description", "法定代表人"));
            
            // 客户字段
            List<Map<String, Object>> customerFields = new ArrayList<>();
            customerFields.add(Map.of("name", "customer.name", "type", "String", "description", "客户名称"));
            customerFields.add(Map.of("name", "customer.taxNo", "type", "String", "description", "客户税号"));
            customerFields.add(Map.of("name", "customer.legalRepresentative", "type", "String", "description", "法定代表人"));
            
            fields.put("invoice", invoiceFields);
            fields.put("supplier", supplierFields);
            fields.put("customer", customerFields);
            
            log.info("域字段列表获取完成");
            return ResponseEntity.ok(fields);
            
        } catch (Exception e) {
            log.error("获取域字段列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取域字段列表失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取可用函数列表
     * 
     * @return 可用函数列表
     */
    @GetMapping("/functions")
    public ResponseEntity<List<Map<String, Object>>> getAvailableFunctions() {
        try {
            log.info("获取可用函数列表");
            
            List<Map<String, Object>> functions = new ArrayList<>();
            
            // has() 函数
            functions.add(Map.of(
                "name", "has",
                "description", "检查字段是否存在且不为空",
                "syntax", "has(field_path)",
                "example", "has(invoice.customer.name)",
                "returnType", "boolean"
            ));
            
            // get_tax_rate() 函数
            functions.add(Map.of(
                "name", "get_tax_rate",
                "description", "根据企业类别获取推荐税率",
                "syntax", "get_tax_rate(category)",
                "example", "get_tax_rate('TECH')",
                "returnType", "BigDecimal"
            ));
            
            // now() 函数
            functions.add(Map.of(
                "name", "now",
                "description", "获取当前日期时间",
                "syntax", "now()",
                "example", "now()",
                "returnType", "LocalDateTime"
            ));
            
            log.info("可用函数列表获取完成，共 {} 个函数", functions.size());
            return ResponseEntity.ok(functions);
            
        } catch (Exception e) {
            log.error("获取可用函数列表失败", e);
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }
    
    /**
     * 重新加载规则
     * 
     * @return 重新加载结果
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadRules() {
        try {
            log.info("重新加载规则配置");
            
            // 重新加载规则
            ruleEngine.loadRules("../shared/config/rules.yaml");
            
            // 获取重新加载后的统计信息
            Map<String, Object> ruleStats = ruleEngine.getRuleStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "规则重新加载成功");
            response.put("totalCompletionRules", ruleStats.get("completion_rules_count"));
            response.put("totalValidationRules", ruleStats.get("validation_rules_count"));
            response.put("activeCompletionRules", ruleStats.get("active_completion_rules"));
            response.put("activeValidationRules", ruleStats.get("active_validation_rules"));
            response.put("rulesLoaded", ruleStats.get("rules_loaded"));
            
            log.info("规则重新加载完成 - 完成规则: {}, 验证规则: {}", 
                ruleStats.get("completion_rules_count"), ruleStats.get("validation_rules_count"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("重新加载规则失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "重新加载规则失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取指定补全规则
     * 
     * @param ruleId 规则ID
     * @return 补全规则详情
     */
    @GetMapping("/completion/{ruleId}")
    public ResponseEntity<Map<String, Object>> getCompletionRule(@PathVariable String ruleId) {
        try {
            log.info("获取补全规则详情，规则ID: {}", ruleId);
            
            Optional<BusinessRule> ruleOpt = businessRuleRepository.findByRuleId(ruleId);
            if (ruleOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则不存在");
                errorResponse.put("message", "未找到指定的补全规则");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            BusinessRule rule = ruleOpt.get();
            if (!"completion".equals(rule.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "指定规则不是补全规则");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", convertBusinessRuleToMap(rule));
            response.put("message", "补全规则获取成功");
            
            log.info("补全规则详情获取完成，规则ID: {}", ruleId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取补全规则详情失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "获取补全规则详情失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取指定校验规则
     * 
     * @param ruleId 规则ID
     * @return 校验规则详情
     */
    @GetMapping("/validation/{ruleId}")
    public ResponseEntity<Map<String, Object>> getValidationRule(@PathVariable String ruleId) {
        try {
            log.info("获取校验规则详情，规则ID: {}", ruleId);
            
            Optional<BusinessRule> ruleOpt = businessRuleRepository.findByRuleId(ruleId);
            if (ruleOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则不存在");
                errorResponse.put("message", "未找到指定的校验规则");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            BusinessRule rule = ruleOpt.get();
            if (!"validation".equals(rule.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "指定规则不是校验规则");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", convertBusinessRuleToMap(rule));
            response.put("message", "校验规则获取成功");
            
            log.info("校验规则详情获取完成，规则ID: {}", ruleId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取校验规则详情失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "获取校验规则详情失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 删除补全规则
     * 
     * @param ruleId 规则ID
     * @return 删除结果
     */
    @DeleteMapping("/completion/{ruleId}")
    public ResponseEntity<Map<String, Object>> deleteCompletionRule(@PathVariable String ruleId) {
        try {
            log.info("删除补全规则，规则ID: {}", ruleId);
            
            Optional<BusinessRule> ruleOpt = businessRuleRepository.findByRuleId(ruleId);
            if (ruleOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则不存在");
                errorResponse.put("message", "未找到指定的补全规则");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            BusinessRule rule = ruleOpt.get();
            if (!"completion".equals(rule.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "指定规则不是补全规则");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            businessRuleRepository.delete(rule);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "补全规则删除成功");
            
            log.info("补全规则删除完成，规则ID: {}", ruleId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("删除补全规则失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "删除补全规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 删除校验规则
     * 
     * @param ruleId 规则ID
     * @return 删除结果
     */
    @DeleteMapping("/validation/{ruleId}")
    public ResponseEntity<Map<String, Object>> deleteValidationRule(@PathVariable String ruleId) {
        try {
            log.info("删除校验规则，规则ID: {}", ruleId);
            
            Optional<BusinessRule> ruleOpt = businessRuleRepository.findByRuleId(ruleId);
            if (ruleOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则不存在");
                errorResponse.put("message", "未找到指定的校验规则");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            BusinessRule rule = ruleOpt.get();
            if (!"validation".equals(rule.getRuleType())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "规则类型不匹配");
                errorResponse.put("message", "指定规则不是校验规则");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            businessRuleRepository.delete(rule);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "校验规则删除成功");
            
            log.info("校验规则删除完成，规则ID: {}", ruleId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("删除校验规则失败，规则ID: {}", ruleId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "删除校验规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 验证表达式语法
     * 
     * @param request 表达式验证请求
     * @return 验证结果
     */
    @PostMapping("/validate-expression")
    public ResponseEntity<Map<String, Object>> validateExpression(@Valid @RequestBody Map<String, Object> request) {
        try {
            String expression = (String) request.get("expression");
            
            log.info("验证表达式语法，表达式: {}", expression);
            
            if (expression == null || expression.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", true);
                errorResponse.put("valid", false);
                errorResponse.put("message", "表达式不能为空");
                return ResponseEntity.ok(errorResponse);
            }
            
            // 使用 SpelRuleEngine 验证表达式
            Map<String, Object> validationResult = spelRuleEngine.validateExpression(expression);
            
            log.info("表达式语法验证完成，结果: {}", validationResult.get("valid"));
            return ResponseEntity.ok(validationResult);
            
        } catch (Exception e) {
            log.error("验证表达式语法失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("valid", false);
            errorResponse.put("error", "验证表达式语法失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 使用 LLM 生成规则
     * 
     * @param request 生成规则请求
     * @return 生成的规则
     */
    @PostMapping("/generate-llm")
    public ResponseEntity<Map<String, Object>> generateRuleWithLLM(@RequestBody Map<String, Object> request) {
        try {
            log.info("使用 LLM 生成规则，请求: {}", request);
            
            // 模拟 LLM 生成规则的响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "LLM 规则生成成功");
            
            // 模拟生成的规则数据
            Map<String, Object> generatedRule = new HashMap<>();
            generatedRule.put("ruleId", UUID.randomUUID().toString());
            generatedRule.put("ruleName", "LLM 生成的规则");
            generatedRule.put("ruleType", request.getOrDefault("ruleType", "validation"));
            generatedRule.put("applyTo", "invoice");
            generatedRule.put("targetField", "amount");
            generatedRule.put("ruleExpression", "amount > 0");
            generatedRule.put("errorMessage", "金额必须大于0");
            generatedRule.put("priority", 1);
            generatedRule.put("isActive", true);
            
            response.put("data", generatedRule);
            
            log.info("LLM 规则生成完成");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("LLM 生成规则失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "LLM 生成规则失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取 LLM 服务状态
     * 
     * @return LLM 服务状态
     */
    @GetMapping("/llm-status")
    public ResponseEntity<Map<String, Object>> getLLMStatus() {
        try {
            log.info("获取 LLM 服务状态");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("available", true);
            response.put("model", "gpt-3.5-turbo");
            response.put("status", "online");
            response.put("message", "LLM 服务正常运行");
            
            log.info("LLM 服务状态获取完成");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取 LLM 服务状态失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("available", false);
            errorResponse.put("error", "获取 LLM 服务状态失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}