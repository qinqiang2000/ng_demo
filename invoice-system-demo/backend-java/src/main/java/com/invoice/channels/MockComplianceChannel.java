package com.invoice.channels;

import com.invoice.domain.InvoiceDomainObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟合规检查渠道
 * 
 * 与 Python MockComplianceChannel 功能完全等价
 * 模拟税务合规、反洗钱等检查流程
 */
@Component
@Slf4j
public class MockComplianceChannel extends BaseChannel {

    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal SUSPICIOUS_AMOUNT_THRESHOLD = new BigDecimal("50000.00");
    
    public MockComplianceChannel() {
        super("MockComplianceChannel", "1.0.0");
    }
    
    @Override
    public ChannelResult process(InvoiceDomainObject invoice, Map<String, Object> options) {
        if (!isEnabled()) {
            return new ChannelResult(false, "合规检查渠道未启用", null, 0);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("开始合规检查 - 发票号: {}, 金额: {}", 
                invoice.getInvoiceNumber(), invoice.getTotalAmount());
            
            // 执行各项合规检查
            Map<String, Object> resultData = new HashMap<>();
            List<String> checkResults = new ArrayList<>();
            boolean overallCompliant = true;
            
            // 1. 税务合规检查
            ComplianceCheckResult taxCheck = performTaxComplianceCheck(invoice);
            checkResults.add("税务合规: " + (taxCheck.passed ? "通过" : "失败 - " + taxCheck.reason));
            if (!taxCheck.passed) overallCompliant = false;
            resultData.put("tax_compliance", taxCheck.toMap());
            
            // 2. 反洗钱检查
            ComplianceCheckResult amlCheck = performAntiMoneyLaunderingCheck(invoice);
            checkResults.add("反洗钱: " + (amlCheck.passed ? "通过" : "失败 - " + amlCheck.reason));
            if (!amlCheck.passed) overallCompliant = false;
            resultData.put("aml_check", amlCheck.toMap());
            
            // 3. 业务逻辑检查
            ComplianceCheckResult businessCheck = performBusinessLogicCheck(invoice);
            checkResults.add("业务逻辑: " + (businessCheck.passed ? "通过" : "失败 - " + businessCheck.reason));
            if (!businessCheck.passed) overallCompliant = false;
            resultData.put("business_logic", businessCheck.toMap());
            
            // 4. 数据完整性检查
            ComplianceCheckResult dataCheck = performDataIntegrityCheck(invoice);
            checkResults.add("数据完整性: " + (dataCheck.passed ? "通过" : "失败 - " + dataCheck.reason));
            if (!dataCheck.passed) overallCompliant = false;
            resultData.put("data_integrity", dataCheck.toMap());
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // 构建结果
            resultData.put("overall_compliant", overallCompliant);
            resultData.put("check_results", checkResults);
            resultData.put("risk_level", calculateRiskLevel(invoice));
            resultData.put("compliance_score", calculateComplianceScore(invoice));
            resultData.put("processing_time_ms", processingTime);
            
            String message = overallCompliant ? 
                "合规检查通过，所有检查项目均符合要求" : 
                "合规检查失败，存在不符合要求的项目";
            
            logOperation("合规检查", invoice.getInvoiceNumber(), overallCompliant, message);
            
            return new ChannelResult(overallCompliant, message, resultData, processingTime);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            String errorMessage = "合规检查处理异常: " + e.getMessage();
            logOperation("合规检查", invoice.getInvoiceNumber(), false, errorMessage);
            
            Map<String, Object> errorData = Map.of(
                "error", e.getMessage(),
                "processing_time_ms", processingTime
            );
            
            return new ChannelResult(false, errorMessage, errorData, processingTime);
        }
    }
    
    @Override
    public ValidationResult validate(InvoiceDomainObject invoice) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 基本验证
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
            errors.add("发票号不能为空");
        }
        
        if (invoice.getTotalAmount() == null || invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("发票金额必须大于零");
        }
        
        if (invoice.getSupplier() == null) {
            errors.add("供应商信息不能为空");
        }
        
        if (invoice.getCustomer() == null) {
            errors.add("客户信息不能为空");
        }
        
        // 合规性警告
        if (invoice.getTotalAmount() != null && 
            invoice.getTotalAmount().compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            warnings.add("大额发票，需要额外审查");
        }
        
        if (invoice.getIssueDate() != null && 
            invoice.getIssueDate().isAfter(LocalDate.now())) {
            warnings.add("发票开票日期不能晚于当前日期");
        }
        
        boolean valid = errors.isEmpty();
        String summary = String.format("验证结果: %s, 错误: %d, 警告: %d", 
            valid ? "通过" : "失败", errors.size(), warnings.size());
        
        return new ValidationResult(valid, 
            errors.toArray(new String[0]), 
            warnings.toArray(new String[0]), 
            summary);
    }
    
    @Override
    public ChannelStatus getStatus() {
        return enabled ? ChannelStatus.ACTIVE : ChannelStatus.INACTIVE;
    }
    
    @Override
    public String[] getSupportedOperations() {
        return new String[]{"tax_compliance", "aml_check", "business_logic", "data_integrity", "risk_assessment"};
    }
    
    @Override
    protected String getDescription() {
        return "模拟合规检查渠道，提供税务合规、反洗钱、业务逻辑和数据完整性检查";
    }
    
    /**
     * 税务合规检查
     */
    private ComplianceCheckResult performTaxComplianceCheck(InvoiceDomainObject invoice) {
        log.debug("执行税务合规检查 - 发票号: {}", invoice.getInvoiceNumber());
        
        // 模拟税务合规检查逻辑
        if (invoice.getSupplier() != null && 
            (invoice.getSupplier().getTaxNo() == null || invoice.getSupplier().getTaxNo().trim().isEmpty()) &&
            invoice.getTotalAmount().compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            return new ComplianceCheckResult(false, "大额发票缺少供应商税号");
        }
        
        if (invoice.getTaxAmount() == null && invoice.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            return new ComplianceCheckResult(false, "发票缺少税额信息");
        }
        
        return new ComplianceCheckResult(true, "税务合规检查通过");
    }
    
    /**
     * 反洗钱检查
     */
    private ComplianceCheckResult performAntiMoneyLaunderingCheck(InvoiceDomainObject invoice) {
        log.debug("执行反洗钱检查 - 发票号: {}", invoice.getInvoiceNumber());
        
        // 模拟反洗钱检查逻辑
        if (invoice.getTotalAmount().compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
            return new ComplianceCheckResult(false, "疑似洗钱：金额异常大");
        }
        
        // 检查供应商和客户是否为同一实体
        if (invoice.getSupplier() != null && invoice.getCustomer() != null &&
            invoice.getSupplier().getName().equals(invoice.getCustomer().getName())) {
            return new ComplianceCheckResult(false, "疑似洗钱：供应商和客户为同一实体");
        }
        
        return new ComplianceCheckResult(true, "反洗钱检查通过");
    }
    
    /**
     * 业务逻辑检查
     */
    private ComplianceCheckResult performBusinessLogicCheck(InvoiceDomainObject invoice) {
        log.debug("执行业务逻辑检查 - 发票号: {}", invoice.getInvoiceNumber());
        
        // 模拟业务逻辑检查
        if (invoice.getIssueDate() != null && invoice.getDueDate() != null &&
            invoice.getDueDate().isBefore(invoice.getIssueDate())) {
            return new ComplianceCheckResult(false, "到期日期不能早于开票日期");
        }
        
        if (invoice.getItems() != null && invoice.getItems().isEmpty() &&
            invoice.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            return new ComplianceCheckResult(false, "有金额的发票必须包含明细项");
        }
        
        return new ComplianceCheckResult(true, "业务逻辑检查通过");
    }
    
    /**
     * 数据完整性检查
     */
    private ComplianceCheckResult performDataIntegrityCheck(InvoiceDomainObject invoice) {
        log.debug("执行数据完整性检查 - 发票号: {}", invoice.getInvoiceNumber());
        
        // 检查必要字段完整性
        List<String> missingFields = new ArrayList<>();
        
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
            missingFields.add("发票号");
        }
        
        if (invoice.getTotalAmount() == null) {
            missingFields.add("总金额");
        }
        
        if (invoice.getIssueDate() == null) {
            missingFields.add("开票日期");
        }
        
        if (!missingFields.isEmpty()) {
            return new ComplianceCheckResult(false, "缺少必要字段: " + String.join(", ", missingFields));
        }
        
        return new ComplianceCheckResult(true, "数据完整性检查通过");
    }
    
    /**
     * 计算风险等级
     */
    private String calculateRiskLevel(InvoiceDomainObject invoice) {
        if (invoice.getTotalAmount().compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
            return "高风险";
        } else if (invoice.getTotalAmount().compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            return "中风险";
        } else {
            return "低风险";
        }
    }
    
    /**
     * 计算合规分数
     */
    private int calculateComplianceScore(InvoiceDomainObject invoice) {
        int score = 100;
        
        // 根据各种因素调整分数
        if (invoice.getSupplier() != null && 
            (invoice.getSupplier().getTaxNo() == null || invoice.getSupplier().getTaxNo().trim().isEmpty())) {
            score -= 10;
        }
        
        if (invoice.getTaxAmount() == null) {
            score -= 5;
        }
        
        if (invoice.getTotalAmount().compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            score -= 5;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * 合规检查结果内部类
     */
    private static class ComplianceCheckResult {
        final boolean passed;
        final String reason;
        
        ComplianceCheckResult(boolean passed, String reason) {
            this.passed = passed;
            this.reason = reason;
        }
        
        Map<String, Object> toMap() {
            return Map.of(
                "passed", passed,
                "reason", reason,
                "timestamp", java.time.LocalDateTime.now().toString()
            );
        }
    }
}