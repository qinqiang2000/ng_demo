package com.invoice.controllers;

import com.invoice.dto.ProcessInvoiceRequest;
import com.invoice.services.WorkflowOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * 工作流控制器
 * 
 * 提供工作流编排相关的 REST API
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@Tag(name = "Workflow", description = "工作流编排相关接口")
public class WorkflowController {

    private final WorkflowOrchestrationService workflowService;
    
    /**
     * 执行完整的发票处理工作流
     * 
     * @param request 处理请求
     * @return 工作流执行结果
     */
    @PostMapping("/execute")
    @Operation(
        summary = "执行发票处理工作流",
        description = "执行完整的发票处理工作流，包括数据转换、核心处理、合规检查和投递等步骤"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "工作流执行成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = WorkflowOrchestrationService.WorkflowResult.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "工作流执行失败"
        )
    })
    public ResponseEntity<WorkflowOrchestrationService.WorkflowResult> executeWorkflow(
            @Parameter(
                description = "发票处理请求对象",
                required = true
            )
            @Valid @RequestBody ProcessInvoiceRequest request) {
        
        log.info("收到工作流执行请求 - 数据类型: {}, 数据长度: {}", 
            request.getActualDataType(), 
            request.getActualData() != null ? request.getActualData().length() : 0);
        
        try {
            WorkflowOrchestrationService.WorkflowResult result = workflowService.executeInvoiceWorkflow(request);
            
            log.info("工作流执行完成 - 工作流ID: {}, 成功: {}, 总耗时: {}ms", 
                result.getWorkflowId(), 
                result.isSuccess(), 
                result.getTotalProcessingTimeMs());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("工作流执行异常", e);
            
            // 返回错误的工作流结果
            WorkflowOrchestrationService.WorkflowResult errorResult = 
                WorkflowOrchestrationService.WorkflowResult.builder()
                    .workflowId("ERROR-" + System.currentTimeMillis())
                    .startTime(java.time.LocalDateTime.now())
                    .endTime(java.time.LocalDateTime.now())
                    .success(false)
                    .message("工作流执行异常: " + e.getMessage())
                    .steps(java.util.List.of())
                    .totalProcessingTimeMs(0)
                    .build();
            
            return ResponseEntity.status(500).body(errorResult);
        }
    }
    
    /**
     * 获取工作流统计信息
     * 
     * @return 工作流统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getWorkflowStats() {
        log.debug("获取工作流统计信息");
        
        try {
            Map<String, Object> stats = workflowService.getWorkflowStats();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("获取工作流统计信息失败", e);
            
            Map<String, Object> errorStats = Map.of(
                "error", "获取统计信息失败: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.status(500).body(errorStats);
        }
    }
    
    /**
     * 健康检查端点
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "healthy",
            "service", "WorkflowOrchestrationService",
            "timestamp", java.time.LocalDateTime.now().toString(),
            "version", "1.0.0"
        );
        
        return ResponseEntity.ok(health);
    }
}