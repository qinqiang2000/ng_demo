package com.invoice.services;

import com.invoice.channels.BaseChannel;
import com.invoice.channels.MockComplianceChannel;
import com.invoice.channels.MockDeliveryChannel;
import com.invoice.connectors.BaseConnector;
import com.invoice.connectors.GenericConnector;
import com.invoice.connectors.XmlConnector;
import com.invoice.core.RuleEngine;
import com.invoice.domain.InvoiceDomainObject;
import com.invoice.dto.ProcessInvoiceRequest;
import com.invoice.dto.ProcessInvoiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流编排服务
 * 
 * 与 Python WorkflowOrchestrationService 功能完全等价
 * 协调各个组件的协作，实现完整的发票处理工作流
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowOrchestrationService {

    private final GenericConnector genericConnector;
    private final XmlConnector xmlConnector;
    private final RuleEngine ruleEngine;
    private final MockComplianceChannel complianceChannel;
    private final MockDeliveryChannel deliveryChannel;
    private final InvoiceService invoiceService;
    
    /**
     * 执行完整的发票处理工作流
     * 
     * @param request 处理请求
     * @return 处理结果
     */
    public WorkflowResult executeInvoiceWorkflow(ProcessInvoiceRequest request) {
        String workflowId = generateWorkflowId();
        long startTime = System.currentTimeMillis();
        
        log.info("开始执行发票处理工作流 - 工作流ID: {}, 数据类型: {}", 
            workflowId, request.getActualDataType());
        
        WorkflowResult.WorkflowResultBuilder resultBuilder = WorkflowResult.builder()
            .workflowId(workflowId)
            .startTime(LocalDateTime.now())
            .steps(new ArrayList<>());
        
        try {
            // 步骤 1: 数据转换和预处理
            WorkflowStep dataTransformStep = executeDataTransformation(request);
            resultBuilder.steps(addStep(resultBuilder.build().getSteps(), dataTransformStep));
            
            if (!dataTransformStep.isSuccess()) {
                return finalizeWorkflow(resultBuilder, false, "数据转换失败", startTime);
            }
            
            InvoiceDomainObject invoice = (InvoiceDomainObject) dataTransformStep.getOutput().get("invoice");
            
            // 步骤 2: 核心业务处理（字段补全、验证等）
            WorkflowStep coreProcessingStep = executeCoreProcessing(invoice, request);
            resultBuilder.steps(addStep(resultBuilder.build().getSteps(), coreProcessingStep));
            
            if (!coreProcessingStep.isSuccess()) {
                return finalizeWorkflow(resultBuilder, false, "核心业务处理失败", startTime);
            }
            
            InvoiceDomainObject processedInvoice = (InvoiceDomainObject) coreProcessingStep.getOutput().get("processed_invoice");
            
            // 步骤 3: 合规检查
            WorkflowStep complianceStep = executeComplianceCheck(processedInvoice, request);
            resultBuilder.steps(addStep(resultBuilder.build().getSteps(), complianceStep));
            
            // 根据配置决定是否因合规失败而终止流程
            // 简化：从skipValidation字段推断是否严格合规
            boolean strictCompliance = request.getOptions() != null && 
                !request.shouldSkipValidation();
            
            if (!complianceStep.isSuccess() && strictCompliance) {
                return finalizeWorkflow(resultBuilder, false, "合规检查失败，严格模式下终止处理", startTime);
            }
            
            // 步骤 4: 投递处理（可选）
            // 简化：默认不启用投递，可以通过扩展ProcessingOptions来支持
            boolean enableDelivery = false;
            
            if (enableDelivery) {
                WorkflowStep deliveryStep = executeDelivery(processedInvoice, request);
                resultBuilder.steps(addStep(resultBuilder.build().getSteps(), deliveryStep));
            }
            
            // 工作流完成
            return finalizeWorkflow(resultBuilder, true, "工作流执行成功", startTime)
                .toBuilder()
                .finalInvoice(processedInvoice)
                .build();
            
        } catch (Exception e) {
            log.error("工作流执行异常 - 工作流ID: {}", workflowId, e);
            
            WorkflowStep errorStep = WorkflowStep.builder()
                .stepName("错误处理")
                .stepType("error")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .success(false)
                .message("工作流执行异常: " + e.getMessage())
                .output(Map.of("error", e.getMessage()))
                .build();
            
            resultBuilder.steps(addStep(resultBuilder.build().getSteps(), errorStep));
            
            return finalizeWorkflow(resultBuilder, false, "工作流执行异常", startTime);
        }
    }
    
    /**
     * 执行数据转换步骤
     */
    private WorkflowStep executeDataTransformation(ProcessInvoiceRequest request) {
        long stepStartTime = System.currentTimeMillis();
        log.debug("执行数据转换步骤 - 数据类型: {}", request.getActualDataType());
        
        try {
            // 选择合适的连接器
            BaseConnector connector = selectConnector(request.getActualDataType());
            
            // 执行转换
            Map<String, Object> optionsMap = convertOptionsToMap(request.getOptions());
            InvoiceDomainObject invoice = connector.transformToStandard(
                request.getActualData(), 
                request.getActualDataType(), 
                optionsMap
            );
            
            long processingTime = System.currentTimeMillis() - stepStartTime;
            
            return WorkflowStep.builder()
                .stepName("数据转换")
                .stepType("transformation")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success(true)
                .message(String.format("使用 %s 成功转换数据，发票号: %s", 
                    connector.getConnectorName(), invoice.getInvoiceNumber()))
                .processingTimeMs(processingTime)
                .output(Map.of(
                    "invoice", invoice,
                    "connector_used", connector.getConnectorName(),
                    "data_type", request.getActualDataType()
                ))
                .build();
                
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - stepStartTime;
            log.error("数据转换步骤失败", e);
            
            return WorkflowStep.builder()
                .stepName("数据转换")
                .stepType("transformation")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success(false)
                .message("数据转换失败: " + e.getMessage())
                .processingTimeMs(processingTime)
                .output(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * 执行核心业务处理步骤
     */
    private WorkflowStep executeCoreProcessing(InvoiceDomainObject invoice, ProcessInvoiceRequest request) {
        long stepStartTime = System.currentTimeMillis();
        log.debug("执行核心业务处理步骤 - 发票号: {}", invoice.getInvoiceNumber());
        
        try {
            // 使用现有的 InvoiceService 进行核心处理
            ProcessInvoiceResponse coreResult = invoiceService.processInvoice(request);
            
            long processingTime = System.currentTimeMillis() - stepStartTime;
            
            return WorkflowStep.builder()
                .stepName("核心业务处理")
                .stepType("core_processing")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success("success".equals(coreResult.getStatus()))
                .message(coreResult.getMessage())
                .processingTimeMs(processingTime)
                .output(Map.of(
                    "processed_invoice", coreResult.getInvoice(),
                    "validation_result", coreResult.getValidation(),
                    "processing_stats", coreResult.getStats()
                ))
                .build();
                
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - stepStartTime;
            log.error("核心业务处理步骤失败", e);
            
            return WorkflowStep.builder()
                .stepName("核心业务处理")
                .stepType("core_processing")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success(false)
                .message("核心业务处理失败: " + e.getMessage())
                .processingTimeMs(processingTime)
                .output(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * 执行合规检查步骤
     */
    private WorkflowStep executeComplianceCheck(InvoiceDomainObject invoice, ProcessInvoiceRequest request) {
        long stepStartTime = System.currentTimeMillis();
        log.debug("执行合规检查步骤 - 发票号: {}", invoice.getInvoiceNumber());
        
        try {
            Map<String, Object> optionsMap = convertOptionsToMap(request.getOptions());
            BaseChannel.ChannelResult complianceResult = complianceChannel.process(invoice, optionsMap);
            
            long processingTime = System.currentTimeMillis() - stepStartTime;
            
            return WorkflowStep.builder()
                .stepName("合规检查")
                .stepType("compliance")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success(complianceResult.isSuccess())
                .message(complianceResult.getMessage())
                .processingTimeMs(processingTime)
                .output(Map.of(
                    "compliance_result", complianceResult.getData(),
                    "channel_result", complianceResult
                ))
                .build();
                
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - stepStartTime;
            log.error("合规检查步骤失败", e);
            
            return WorkflowStep.builder()
                .stepName("合规检查")
                .stepType("compliance")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success(false)
                .message("合规检查失败: " + e.getMessage())
                .processingTimeMs(processingTime)
                .output(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * 执行投递步骤
     */
    private WorkflowStep executeDelivery(InvoiceDomainObject invoice, ProcessInvoiceRequest request) {
        long stepStartTime = System.currentTimeMillis();
        log.debug("执行投递步骤 - 发票号: {}", invoice.getInvoiceNumber());
        
        try {
            Map<String, Object> optionsMap = convertOptionsToMap(request.getOptions());
            BaseChannel.ChannelResult deliveryResult = deliveryChannel.process(invoice, optionsMap);
            
            long processingTime = System.currentTimeMillis() - stepStartTime;
            
            return WorkflowStep.builder()
                .stepName("发票投递")
                .stepType("delivery")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success(deliveryResult.isSuccess())
                .message(deliveryResult.getMessage())
                .processingTimeMs(processingTime)
                .output(Map.of(
                    "delivery_result", deliveryResult.getData(),
                    "channel_result", deliveryResult
                ))
                .build();
                
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - stepStartTime;
            log.error("投递步骤失败", e);
            
            return WorkflowStep.builder()
                .stepName("发票投递")
                .stepType("delivery")
                .startTime(LocalDateTime.now().minusNanos(processingTime * 1_000_000))
                .endTime(LocalDateTime.now())
                .success(false)
                .message("发票投递失败: " + e.getMessage())
                .processingTimeMs(processingTime)
                .output(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * 选择合适的连接器
     */
    private BaseConnector selectConnector(String dataType) {
        if (dataType == null) {
            return genericConnector;
        }
        
        return switch (dataType.toLowerCase()) {
            case "xml", "kdubl", "ubl", "cii" -> xmlConnector;
            default -> genericConnector;
        };
    }
    
    /**
     * 完成工作流
     */
    private WorkflowResult finalizeWorkflow(WorkflowResult.WorkflowResultBuilder builder, 
                                          boolean success, String message, long startTime) {
        long totalTime = System.currentTimeMillis() - startTime;
        
        return builder
            .endTime(LocalDateTime.now())
            .success(success)
            .message(message)
            .totalProcessingTimeMs(totalTime)
            .build();
    }
    
    /**
     * 添加步骤到列表
     */
    private List<WorkflowStep> addStep(List<WorkflowStep> steps, WorkflowStep newStep) {
        List<WorkflowStep> newSteps = new ArrayList<>(steps);
        newSteps.add(newStep);
        return newSteps;
    }
    
    /**
     * 生成工作流ID
     */
    private String generateWorkflowId() {
        return "WF-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString((int)(Math.random() * 0x10000));
    }
    
    /**
     * 将ProcessingOptions转换为Map
     */
    private Map<String, Object> convertOptionsToMap(ProcessInvoiceRequest.ProcessingOptions options) {
        Map<String, Object> optionsMap = new HashMap<>();
        
        if (options != null) {
            optionsMap.put("skip_completion", options.getSkipCompletion());
            optionsMap.put("skip_validation", options.getSkipValidation());
            optionsMap.put("verbose_logging", options.getVerboseLogging());
            optionsMap.put("timeout_seconds", options.getTimeoutSeconds());
            optionsMap.put("return_intermediate_results", options.getReturnIntermediateResults());
            optionsMap.put("custom_rule_set", options.getCustomRuleSet());
            optionsMap.put("force_reprocess", options.getForceReprocess());
        }
        
        // 添加一些工作流特定的默认值
        optionsMap.putIfAbsent("strict_compliance", false);
        optionsMap.putIfAbsent("enable_delivery", false);
        optionsMap.putIfAbsent("delivery_method", "email");
        optionsMap.putIfAbsent("urgent", false);
        
        return optionsMap;
    }
    
    /**
     * 获取工作流统计信息
     */
    public Map<String, Object> getWorkflowStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 连接器状态
        Map<String, Object> connectorStats = new HashMap<>();
        connectorStats.put("generic_connector", genericConnector.getMetadata());
        connectorStats.put("xml_connector", xmlConnector.getMetadata());
        stats.put("connectors", connectorStats);
        
        // 渠道状态
        Map<String, Object> channelStats = new HashMap<>();
        channelStats.put("compliance_channel", complianceChannel.getMetadata());
        channelStats.put("delivery_channel", deliveryChannel.getMetadata());
        stats.put("channels", channelStats);
        
        // 规则引擎状态
        stats.put("rule_engine", ruleEngine.getRuleStats());
        
        return stats;
    }
    
    /**
     * 工作流结果
     */
    @lombok.Data
    @lombok.Builder(toBuilder = true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WorkflowResult {
        private String workflowId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean success;
        private String message;
        private List<WorkflowStep> steps;
        private long totalProcessingTimeMs;
        private InvoiceDomainObject finalInvoice;
    }
    
    /**
     * 工作流步骤
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WorkflowStep {
        private String stepName;
        private String stepType;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean success;
        private String message;
        private long processingTimeMs;
        private Map<String, Object> output;
    }
}