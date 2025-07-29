package com.invoice.channels;

import com.invoice.domain.InvoiceDomainObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 模拟投递渠道
 * 
 * 与 Python MockDeliveryChannel 功能完全等价
 * 模拟发票的各种投递方式（邮件、短信、API推送等）
 */
@Component
@Slf4j
public class MockDeliveryChannel extends BaseChannel {

    private final Random random = new Random();
    
    // 模拟投递成功率配置
    private static final double EMAIL_SUCCESS_RATE = 0.95;
    private static final double SMS_SUCCESS_RATE = 0.90;
    private static final double API_SUCCESS_RATE = 0.98;
    private static final double POSTAL_SUCCESS_RATE = 0.85;
    
    public MockDeliveryChannel() {
        super("MockDeliveryChannel", "1.0.0");
    }
    
    @Override
    public ChannelResult process(InvoiceDomainObject invoice, Map<String, Object> options) {
        if (!isEnabled()) {
            return new ChannelResult(false, "投递渠道未启用", null, 0);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("开始发票投递 - 发票号: {}, 投递选项: {}", 
                invoice.getInvoiceNumber(), options);
            
            // 获取投递方式，默认为邮件
            String deliveryMethod = (String) options.getOrDefault("delivery_method", "email");
            boolean urgent = Boolean.parseBoolean(options.getOrDefault("urgent", "false").toString());
            String recipient = (String) options.get("recipient");
            
            Map<String, Object> resultData = new HashMap<>();
            List<DeliveryAttempt> attempts = new ArrayList<>();
            boolean overallSuccess = true;
            
            // 执行投递
            DeliveryAttempt attempt = performDelivery(invoice, deliveryMethod, recipient, urgent);
            attempts.add(attempt);
            
            if (!attempt.success) {
                overallSuccess = false;
                
                // 如果是高优先级或大额发票，尝试备用投递方式
                if (urgent || isHighValueInvoice(invoice)) {
                    log.info("主要投递方式失败，尝试备用投递方式 - 发票号: {}", invoice.getInvoiceNumber());
                    
                    String backupMethod = getBackupDeliveryMethod(deliveryMethod);
                    DeliveryAttempt backupAttempt = performDelivery(invoice, backupMethod, recipient, urgent);
                    attempts.add(backupAttempt);
                    
                    if (backupAttempt.success) {
                        overallSuccess = true;
                    }
                }
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // 构建结果数据
            resultData.put("delivery_attempts", attempts.stream().map(DeliveryAttempt::toMap).toList());
            resultData.put("total_attempts", attempts.size());
            resultData.put("successful_attempts", attempts.stream().mapToInt(a -> a.success ? 1 : 0).sum());
            resultData.put("primary_method", deliveryMethod);
            resultData.put("urgent", urgent);
            resultData.put("recipient", recipient);
            resultData.put("processing_time_ms", processingTime);
            
            // 添加投递统计信息
            resultData.put("delivery_stats", generateDeliveryStats(attempts));
            
            String message = overallSuccess ? 
                String.format("发票投递成功，使用方式: %s", 
                    attempts.stream().filter(a -> a.success).findFirst().map(a -> a.method).orElse("未知")) :
                String.format("发票投递失败，尝试了 %d 种方式", attempts.size());
            
            logOperation("发票投递", invoice.getInvoiceNumber(), overallSuccess, message);
            
            return new ChannelResult(overallSuccess, message, resultData, processingTime);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            String errorMessage = "发票投递处理异常: " + e.getMessage();
            logOperation("发票投递", invoice.getInvoiceNumber(), false, errorMessage);
            
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
        
        if (invoice.getCustomer() == null) {
            errors.add("客户信息不能为空，无法确定投递目标");
        } else {
            // 检查联系方式
            boolean hasContact = false;
            if (invoice.getCustomer().getEmail() != null && !invoice.getCustomer().getEmail().trim().isEmpty()) {
                hasContact = true;
            }
            if (invoice.getCustomer().getPhone() != null && !invoice.getCustomer().getPhone().trim().isEmpty()) {
                hasContact = true;
            }
            
            if (!hasContact) {
                warnings.add("客户缺少联系方式，可能影响投递效果");
            }
        }
        
        // 投递相关警告
        if (isHighValueInvoice(invoice)) {
            warnings.add("高价值发票，建议使用多重投递方式确保送达");
        }
        
        boolean valid = errors.isEmpty();
        String summary = String.format("投递验证结果: %s, 错误: %d, 警告: %d", 
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
        return new String[]{"email", "sms", "api_push", "postal", "multi_channel"};
    }
    
    @Override
    protected String getDescription() {
        return "模拟投递渠道，支持邮件、短信、API推送和邮政投递等多种方式";
    }
    
    /**
     * 执行具体的投递操作
     */
    private DeliveryAttempt performDelivery(InvoiceDomainObject invoice, String method, String recipient, boolean urgent) {
        log.debug("执行投递 - 方式: {}, 发票号: {}, 收件人: {}", method, invoice.getInvoiceNumber(), recipient);
        
        // 模拟投递延迟
        int baseDelay = urgent ? 50 : 200;
        int actualDelay = baseDelay + random.nextInt(100);
        
        try {
            Thread.sleep(actualDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 根据投递方式确定成功率
        double successRate = getSuccessRateForMethod(method);
        boolean success = random.nextDouble() < successRate;
        
        String deliveryId = UUID.randomUUID().toString();
        String status = success ? "delivered" : "failed";
        String message = generateDeliveryMessage(method, success, recipient);
        
        return new DeliveryAttempt(
            deliveryId,
            method,
            recipient,
            success,
            status,
            message,
            LocalDateTime.now(),
            actualDelay
        );
    }
    
    /**
     * 获取投递方式的成功率
     */
    private double getSuccessRateForMethod(String method) {
        return switch (method.toLowerCase()) {
            case "email" -> EMAIL_SUCCESS_RATE;
            case "sms" -> SMS_SUCCESS_RATE;
            case "api_push" -> API_SUCCESS_RATE;
            case "postal" -> POSTAL_SUCCESS_RATE;
            default -> 0.8; // 默认成功率
        };
    }
    
    /**
     * 获取备用投递方式
     */
    private String getBackupDeliveryMethod(String primaryMethod) {
        return switch (primaryMethod.toLowerCase()) {
            case "email" -> "sms";
            case "sms" -> "email";
            case "api_push" -> "email";
            case "postal" -> "email";
            default -> "email";
        };
    }
    
    /**
     * 判断是否为高价值发票
     */
    private boolean isHighValueInvoice(InvoiceDomainObject invoice) {
        return invoice.getTotalAmount() != null && 
               invoice.getTotalAmount().compareTo(new BigDecimal("10000.00")) > 0;
    }
    
    /**
     * 生成投递消息
     */
    private String generateDeliveryMessage(String method, boolean success, String recipient) {
        if (success) {
            return switch (method.toLowerCase()) {
                case "email" -> String.format("邮件已成功发送至 %s", recipient != null ? recipient : "默认邮箱");
                case "sms" -> String.format("短信已成功发送至 %s", recipient != null ? recipient : "默认手机号");
                case "api_push" -> String.format("API推送已成功发送至 %s", recipient != null ? recipient : "默认接口");
                case "postal" -> String.format("邮政投递已安排至 %s", recipient != null ? recipient : "默认地址");
                default -> "投递成功";
            };
        } else {
            return switch (method.toLowerCase()) {
                case "email" -> "邮件发送失败：可能的原因包括邮箱地址无效、邮件服务器故障等";
                case "sms" -> "短信发送失败：可能的原因包括手机号无效、运营商网络问题等";
                case "api_push" -> "API推送失败：可能的原因包括接口不可用、认证失败等";
                case "postal" -> "邮政投递失败：可能的原因包括地址无效、邮政服务异常等";
                default -> "投递失败";
            };
        }
    }
    
    /**
     * 生成投递统计信息
     */
    private Map<String, Object> generateDeliveryStats(List<DeliveryAttempt> attempts) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("total_attempts", attempts.size());
        stats.put("successful_attempts", attempts.stream().mapToInt(a -> a.success ? 1 : 0).sum());
        stats.put("failed_attempts", attempts.stream().mapToInt(a -> a.success ? 0 : 1).sum());
        stats.put("average_delay_ms", attempts.stream().mapToInt(a -> a.delayMs).average().orElse(0.0));
        stats.put("methods_used", attempts.stream().map(a -> a.method).distinct().toList());
        
        return stats;
    }
    
    /**
     * 投递尝试记录
     */
    private static class DeliveryAttempt {
        final String deliveryId;
        final String method;
        final String recipient;
        final boolean success;
        final String status;
        final String message;
        final LocalDateTime timestamp;
        final int delayMs;
        
        DeliveryAttempt(String deliveryId, String method, String recipient, boolean success, 
                       String status, String message, LocalDateTime timestamp, int delayMs) {
            this.deliveryId = deliveryId;
            this.method = method;
            this.recipient = recipient;
            this.success = success;
            this.status = status;
            this.message = message;
            this.timestamp = timestamp;
            this.delayMs = delayMs;
        }
        
        Map<String, Object> toMap() {
            return Map.of(
                "delivery_id", deliveryId,
                "method", method,
                "recipient", recipient != null ? recipient : "未指定",
                "success", success,
                "status", status,
                "message", message,
                "timestamp", timestamp.toString(),
                "delay_ms", delayMs
            );
        }
    }
}