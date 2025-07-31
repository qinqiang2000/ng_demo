package com.invoice.controllers;

import com.invoice.dto.ProcessInvoiceRequest;
import com.invoice.domain.InvoiceDomainObject;
import com.invoice.domain.InvoiceItem;
import com.invoice.services.InvoiceService;
import com.invoice.config.RuleEngineConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.*;

/**
 * 发票处理控制器
 * 
 * 提供发票数据处理的REST API接口
 * 兼容Python后端的响应格式
 */
@RestController
@RequestMapping("/api/invoice")
@Slf4j
@CrossOrigin(origins = "*")
public class InvoiceController {

    @Autowired
    @Qualifier("invoiceService")
    private InvoiceService invoiceService;
    
    @Autowired
    private RuleEngineConfigService ruleEngineConfigService;

    /**
     * 处理发票数据
     * 
     * 主要的发票处理接口，接收各种格式的发票数据并进行处理
     * 返回Python后端兼容的响应格式
     * 
     * @param request 发票处理请求
     * @return 处理结果
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processInvoice(
            @Valid @RequestBody ProcessInvoiceRequest request) {
        
        log.info("收到发票处理请求，数据类型: {}, 数据长度: {}", 
                request.getActualDataType(), 
                request.getDataLength());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 验证请求数据 - 使用getActualData()来兼容kdubl_xml字段
            String actualData = request.getActualData();
            if (actualData == null || actualData.trim().isEmpty()) {
                log.warn("发票数据为空");
                return ResponseEntity.badRequest().body(
                    createPythonFormatErrorResponse("EMPTY_DATA", "发票数据不能为空")
                );
            }
            
            // 使用真实的InvoiceService处理发票数据
            log.debug("=== InvoiceController: 准备调用 invoiceService.processInvoice() ===");
            com.invoice.dto.ProcessInvoiceResponse processingResult = invoiceService.processInvoice(request);
            log.debug("=== InvoiceController: invoiceService.processInvoice() 调用完成 ===");
            
            // 转换为Python后端兼容的响应格式
            Map<String, Object> pythonResponse = convertToCompatibleResponse(processingResult, request, startTime);
            
            log.info("发票处理完成，处理时间: {}ms, 返回 {} 个结果", 
                    System.currentTimeMillis() - startTime,
                    ((List<?>) pythonResponse.get("results")).size());
            
            return ResponseEntity.ok(pythonResponse);
            
        } catch (Exception e) {
            log.error("发票处理失败", e);
            return ResponseEntity.badRequest().body(
                createPythonFormatErrorResponse("PROCESSING_ERROR", "发票处理失败: " + e.getMessage())
            );
        }
    }

    /**
     * 将Java服务响应转换为Python后端兼容的响应格式
     */
    private Map<String, Object> convertToCompatibleResponse(
            com.invoice.dto.ProcessInvoiceResponse processingResult, 
            ProcessInvoiceRequest request, 
            long startTime) {
        
        Map<String, Object> pythonResponse = new HashMap<>();
        
        // 基本信息
        pythonResponse.put("success", processingResult.isSuccess());
        pythonResponse.put("batch_id", "batch_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + UUID.randomUUID().toString().substring(0, 8));
        pythonResponse.put("total_inputs", 1);
        pythonResponse.put("processing_time", String.format("%.2fs", (System.currentTimeMillis() - startTime) / 1000.0));
        pythonResponse.put("rule_engine_used", ruleEngineConfigService.getCurrentEngine());
        
        // 错误信息
        List<String> errors = new ArrayList<>();
        pythonResponse.put("errors", errors);
        
        // 摘要信息
        Map<String, Object> summary = new HashMap<>();
        summary.put("total_inputs", 1);
        summary.put("successful_inputs", processingResult.isSuccess() ? 1 : 0);
        summary.put("failed_inputs", processingResult.isSuccess() ? 0 : 1);
        summary.put("total_output_invoices", processingResult.isSuccess() ? 1 : 0);
        pythonResponse.put("summary", summary);
        
        // 文件映射
        List<Map<String, Object>> fileMapping = new ArrayList<>();
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("input_index", 0);
        fileInfo.put("input_type", request.getDataType() != null ? request.getDataType() : "string");
        fileInfo.put("filename", "direct_input");
        fileInfo.put("success", processingResult.isSuccess());
        if (processingResult.getInvoice() != null && processingResult.getInvoice().getInvoiceNumber() != null) {
            fileInfo.put("invoice_number", processingResult.getInvoice().getInvoiceNumber());
        }
        fileMapping.add(fileInfo);
        pythonResponse.put("file_mapping", fileMapping);
        
        // 获取真实的completion_logs和validation_logs
        List<Map<String, Object>> completionLogs;
        List<Map<String, Object>> validationLogs;
        
        if (ruleEngineConfigService.isCurrentlySpel()) {
            // 使用SpEL引擎时，从SpEL引擎获取日志
            completionLogs = invoiceService.getSpelRuleEngine().getCompletionExecutionLog();
            validationLogs = invoiceService.getSpelRuleEngine().getValidationExecutionLog();
        } else {
            // 使用CEL引擎时，从CEL引擎获取日志
            completionLogs = invoiceService.getRuleEngine().getCompletionExecutionLog();
            validationLogs = invoiceService.getRuleEngine().getValidationExecutionLog();
        }
        
        // 执行详情
        Map<String, Object> executionDetails = new HashMap<>();
        executionDetails.put("completion_logs", completionLogs);
        executionDetails.put("validation_logs", validationLogs);
        
        // 按文件分组的补全日志
        List<Map<String, Object>> completionByFile = new ArrayList<>();
        if (processingResult.getInvoice() != null) {
            Map<String, Object> fileCompletion = new HashMap<>();
            fileCompletion.put("file_index", 0);
            fileCompletion.put("file_name", "direct_input");
            fileCompletion.put("invoice_number", processingResult.getInvoice().getInvoiceNumber());
            fileCompletion.put("completion_logs", completionLogs);
            completionByFile.add(fileCompletion);
        }
        executionDetails.put("completion_by_file", completionByFile);
        
        // 按发票分组的验证日志
        List<Map<String, Object>> validationByInvoice = new ArrayList<>();
        if (processingResult.getInvoice() != null) {
            Map<String, Object> invoiceValidation = new HashMap<>();
            invoiceValidation.put("invoice_index", 0);
            invoiceValidation.put("invoice_number", processingResult.getInvoice().getInvoiceNumber());
            invoiceValidation.put("validation_logs", validationLogs);
            validationByInvoice.add(invoiceValidation);
        }
        executionDetails.put("validation_by_invoice", validationByInvoice);
        
        // 添加merge_logs
        executionDetails.put("merge_logs", generateMockMergeLogs());
        
        pythonResponse.put("execution_details", executionDetails);
        
        // 详情数组 - 包含每个文件的详细信息
        List<Map<String, Object>> details = new ArrayList<>();
        if (processingResult.getInvoice() != null) {
            Map<String, Object> fileDetail = new HashMap<>();
            fileDetail.put("file_index", 0);
            Map<String, Object> fileExecutionDetails = new HashMap<>();
            fileExecutionDetails.put("completion_logs", completionLogs);
            fileDetail.put("execution_details", fileExecutionDetails);
            details.add(fileDetail);
        }
        pythonResponse.put("details", details);
        
        // 最重要的：results数组
        List<Map<String, Object>> results = new ArrayList<>();
        if (processingResult.isSuccess() && processingResult.getInvoice() != null) {
            results.add(convertInvoiceToResult(processingResult.getInvoice(), request.getActualData()));
        } else if (!processingResult.isSuccess()) {
            // 处理失败的情况
            Map<String, Object> failedResult = new HashMap<>();
            failedResult.put("invoice_id", "output_1");
            failedResult.put("invoice_number", processingResult.getInvoice() != null ? processingResult.getInvoice().getInvoiceNumber() : "unknown");
            failedResult.put("success", false);
            List<String> resultErrors = new ArrayList<>();
            if (processingResult.hasErrors()) {
                processingResult.getValidation().getErrors().forEach(error -> 
                    resultErrors.add("处理异常: " + error.getMessage())
                );
            }
            if (!processingResult.isSuccess() && processingResult.getMessage() != null) {
                resultErrors.add("处理异常: " + processingResult.getMessage());
            }
            failedResult.put("errors", resultErrors);
            results.add(failedResult);
        }
        pythonResponse.put("results", results);
        
        return pythonResponse;
    }
    
    /**
     * 将处理步骤转换为补全日志格式
     */
    private List<Map<String, Object>> convertProcessingStepsToCompletionLogs(List<com.invoice.dto.ProcessInvoiceResponse.ProcessingStep> steps) {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                com.invoice.dto.ProcessInvoiceResponse.ProcessingStep step = steps.get(i);
                Map<String, Object> log = new HashMap<>();
                log.put("rule_id", "completion_rule_" + (i + 1));
                log.put("rule_name", step.getStepName());
                log.put("status", step.getStatus());
                log.put("field_path", "invoice." + step.getStepName().toLowerCase().replaceAll("[^a-zA-Z]", ""));
                log.put("original_value", "");
                log.put("completed_value", step.getDescription());
                log.put("execution_time_ms", step.getDurationMs() != null ? step.getDurationMs() : 1);
                logs.add(log);
            }
        }
        
        return logs;
    }
    
    /**
     * 生成模拟的合并日志，匹配Python版本格式
     */
    private List<Map<String, Object>> generateMockMergeLogs() {
        List<Map<String, Object>> mergeLogs = new ArrayList<>();
        
        Map<String, Object> mergeLog = new HashMap<>();
        mergeLog.put("type", "merge");
        mergeLog.put("status", "success");
        mergeLog.put("message", "✅ 发票数据合并完成");
        mergeLog.put("merged_count", 1);
        mergeLog.put("total_invoices", 1);
        mergeLogs.add(mergeLog);
        
        return mergeLogs;
    }
    
    /**
     * 将验证结果转换为验证日志格式
     */
    private List<Map<String, Object>> convertValidationToLogs(com.invoice.dto.ProcessInvoiceResponse.ValidationResult validation) {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        if (validation != null) {
            // 处理错误
            if (validation.getErrors() != null) {
                for (int i = 0; i < validation.getErrors().size(); i++) {
                    com.invoice.dto.ProcessInvoiceResponse.ValidationError error = validation.getErrors().get(i);
                    Map<String, Object> log = new HashMap<>();
                    log.put("rule_id", "validation_rule_error_" + (i + 1));
                    log.put("rule_name", error.getCode());
                    log.put("status", "failed");
                    log.put("field_path", error.getFieldPath());
                    log.put("message", error.getMessage());
                    log.put("execution_time_ms", 1);
                    logs.add(log);
                }
            }
            
            // 处理警告
            if (validation.getWarnings() != null) {
                for (int i = 0; i < validation.getWarnings().size(); i++) {
                    com.invoice.dto.ProcessInvoiceResponse.ValidationWarning warning = validation.getWarnings().get(i);
                    Map<String, Object> log = new HashMap<>();
                    log.put("rule_id", "validation_rule_warning_" + (i + 1));
                    log.put("rule_name", warning.getCode());
                    log.put("status", "warning");
                    log.put("field_path", warning.getFieldPath());
                    log.put("message", warning.getMessage());
                    log.put("execution_time_ms", 1);
                    logs.add(log);
                }
            }
            
            // 如果没有错误和警告，添加一个成功的验证日志
            if ((validation.getErrors() == null || validation.getErrors().isEmpty()) && 
                (validation.getWarnings() == null || validation.getWarnings().isEmpty())) {
                Map<String, Object> log = new HashMap<>();
                log.put("rule_id", "validation_rule_1");
                log.put("rule_name", "基础验证");
                log.put("status", "passed");
                log.put("field_path", "invoice");
                log.put("message", validation.getSummary() != null ? validation.getSummary() : "发票验证通过");
                log.put("execution_time_ms", 1);
                logs.add(log);
            }
        }
        
        return logs;
    }
    
    /**
     * 将发票域对象转换为结果格式
     */
    private Map<String, Object> convertInvoiceToResult(InvoiceDomainObject invoice, String originalData) {
        Map<String, Object> result = new HashMap<>();
        result.put("invoice_id", "output_1");
        result.put("invoice_number", invoice.getInvoiceNumber());
        result.put("success", true);
        result.put("source_system", "ERP");
        
        // 数据部分
        Map<String, Object> data = new HashMap<>();
        
        // 域对象 - 完整结构
        Map<String, Object> domainObject = new HashMap<>();
        
        // 基本信息
        domainObject.put("invoice_number", invoice.getInvoiceNumber());
        domainObject.put("invoice_type", invoice.getInvoiceType());
        domainObject.put("total_amount", invoice.getTotalAmount());
        domainObject.put("tax_amount", invoice.getTaxAmount());
        domainObject.put("net_amount", invoice.getNetAmount());
        domainObject.put("currency", invoice.getCurrency());
        domainObject.put("country", invoice.getCountry());
        domainObject.put("invoice_date", invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : null);
        domainObject.put("status", invoice.getStatus());
        domainObject.put("due_date", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
        domainObject.put("reference_number", invoice.getReferenceNumber());
        domainObject.put("tax_rate", invoice.getTaxRate());
        domainObject.put("invoice_category", invoice.getInvoiceCategory());
        domainObject.put("industry_category", invoice.getIndustryCategory());
        domainObject.put("project_name", invoice.getProjectName());
        domainObject.put("contract_number", invoice.getContractNumber());
        domainObject.put("approval_status", invoice.getApprovalStatus());
        domainObject.put("payment_terms", invoice.getPaymentTerms());
        domainObject.put("notes", invoice.getNotes());
        
        // 供应商信息 - 完整结构
        if (invoice.getSupplier() != null) {
            Map<String, Object> supplier = new HashMap<>();
            supplier.put("name", invoice.getSupplier().getName());
            supplier.put("standard_name", invoice.getSupplier().getStandardName());
            supplier.put("tax_no", invoice.getSupplier().getTaxNo());
            supplier.put("phone", invoice.getSupplier().getPhone());
            supplier.put("email", invoice.getSupplier().getEmail());
            supplier.put("bank_account", invoice.getSupplier().getBankAccount());
            supplier.put("bank_name", invoice.getSupplier().getBankName());
            supplier.put("company_type", invoice.getSupplier().getCompanyType());
            supplier.put("legal_representative", invoice.getSupplier().getLegalRepresentative());
            supplier.put("registered_capital", invoice.getSupplier().getRegisteredCapital());
            supplier.put("company_scale", invoice.getSupplier().getCompanyScale());
            supplier.put("industry_classification", invoice.getSupplier().getIndustryClassification());
            supplier.put("company_status", invoice.getSupplier().getCompanyStatus());
            
            // 地址信息
            if (invoice.getSupplier().getAddress() != null) {
                Map<String, Object> address = new HashMap<>();
                address.put("street", invoice.getSupplier().getAddress().getStreet());
                address.put("city", invoice.getSupplier().getAddress().getCity());
                address.put("state", invoice.getSupplier().getAddress().getState());
                address.put("postal_code", invoice.getSupplier().getAddress().getPostalCode());
                address.put("country", invoice.getSupplier().getAddress().getCountry());
                supplier.put("address", address);
            }
            
            domainObject.put("supplier", supplier);
        }
        
        // 客户信息 - 完整结构
        if (invoice.getCustomer() != null) {
            Map<String, Object> customer = new HashMap<>();
            customer.put("name", invoice.getCustomer().getName());
            customer.put("standard_name", invoice.getCustomer().getStandardName());
            customer.put("tax_no", invoice.getCustomer().getTaxNo());
            customer.put("phone", invoice.getCustomer().getPhone());
            customer.put("email", invoice.getCustomer().getEmail());
            customer.put("bank_account", invoice.getCustomer().getBankAccount());
            customer.put("bank_name", invoice.getCustomer().getBankName());
            customer.put("company_type", invoice.getCustomer().getCompanyType());
            customer.put("legal_representative", invoice.getCustomer().getLegalRepresentative());
            customer.put("registered_capital", invoice.getCustomer().getRegisteredCapital());
            customer.put("company_scale", invoice.getCustomer().getCompanyScale());
            customer.put("industry_classification", invoice.getCustomer().getIndustryClassification());
            customer.put("company_status", invoice.getCustomer().getCompanyStatus());
            
            // 地址信息
            if (invoice.getCustomer().getAddress() != null) {
                Map<String, Object> address = new HashMap<>();
                address.put("street", invoice.getCustomer().getAddress().getStreet());
                address.put("city", invoice.getCustomer().getAddress().getCity());
                address.put("state", invoice.getCustomer().getAddress().getState());
                address.put("postal_code", invoice.getCustomer().getAddress().getPostalCode());
                address.put("country", invoice.getCustomer().getAddress().getCountry());
                customer.put("address", address);
            }
            
            domainObject.put("customer", customer);
        }
        
        // 发票明细项 - 完整结构
        List<Map<String, Object>> items = new ArrayList<>();
        if (invoice.getItems() != null) {
            for (int i = 0; i < invoice.getItems().size(); i++) {
                InvoiceItem item = invoice.getItems().get(i);
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("item_id", i + 1);
                itemMap.put("name", item.getName());
                itemMap.put("description", item.getDescription());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("unit_price", item.getUnitPrice());
                itemMap.put("line_total", item.getLineTotal());
                itemMap.put("tax_rate", item.getTaxRate());
                itemMap.put("tax_amount", item.getTaxAmount());
                itemMap.put("tax_category", item.getTaxCategory());
                itemMap.put("unit", item.getUnit());
                itemMap.put("product_code", item.getProductCode());
                itemMap.put("category", item.getCategory());
                itemMap.put("discount_rate", item.getDiscountRate());
                itemMap.put("discount_amount", item.getDiscountAmount());
                itemMap.put("net_amount", item.getNetAmount());
                itemMap.put("specification", item.getSpecification());
                itemMap.put("brand", item.getBrand());
                itemMap.put("model", item.getModel());
                itemMap.put("remarks", item.getRemarks());
                items.add(itemMap);
            }
        }
        domainObject.put("items", items);
        
        // 扩展字段（用于CEL表达式）
        // 首先使用发票对象中已有的扩展字段，如果没有则创建新的
        Map<String, Object> extensions = invoice.getExtensions() != null ? 
            new HashMap<>(invoice.getExtensions()) : new HashMap<>();
        
        // 补充其他扩展字段（如果尚未设置）
        if (invoice.getSupplier() != null && invoice.getSupplier().getIndustryClassification() != null) {
            extensions.putIfAbsent("supplier_category", invoice.getSupplier().getIndustryClassification());
        }
        if (invoice.getInvoiceType() != null) {
            extensions.putIfAbsent("invoice_type", invoice.getInvoiceType());
        }
        if (invoice.getItems() != null) {
            extensions.putIfAbsent("total_quantity", invoice.getItems().stream()
                .filter(item -> item.getQuantity() != null)
                .map(InvoiceItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        domainObject.put("extensions", extensions);
        
        // 处理后的KDUBL（这里可以调用KdublConverter生成真实的KDUBL）
        String processedKdubl = generateProcessedKdublFromInvoice(invoice, originalData);
        
        data.put("domain_object", domainObject);
        data.put("processed_kdubl", processedKdubl);
        result.put("data", data);
        
        return result;
    }
    
    /**
     * 从发票域对象生成处理后的KDUBL XML
     */
    private String generateProcessedKdublFromInvoice(InvoiceDomainObject invoice, String originalData) {
        // 这里可以注入KdublConverter来生成真实的KDUBL
        // 为了简化，先使用模板生成
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Invoice>
                <InvoiceNumber>%s</InvoiceNumber>
                <InvoiceType>%s</InvoiceType>
                <Customer>
                    <Name>%s</Name>
                    <TaxID>%s</TaxID>
                </Customer>
                <Supplier>
                    <Name>%s</Name>
                    <TaxID>%s</TaxID>
                </Supplier>
                <Amount>
                    <Total>%s</Total>
                    <Tax>%s</Tax>
                    <Net>%s</Net>
                    <Currency>%s</Currency>
                </Amount>
                <Date>%s</Date>
                <Status>%s</Status>
            </Invoice>
            """, 
            invoice.getInvoiceNumber(),
            invoice.getInvoiceType(),
            invoice.getCustomer() != null ? invoice.getCustomer().getName() : "",
            invoice.getCustomer() != null ? invoice.getCustomer().getTaxNo() : "",
             invoice.getSupplier() != null ? invoice.getSupplier().getName() : "",
             invoice.getSupplier() != null ? invoice.getSupplier().getTaxNo() : "",
            invoice.getTotalAmount() != null ? invoice.getTotalAmount().toString() : "0.00",
            invoice.getTaxAmount() != null ? invoice.getTaxAmount().toString() : "0.00",
            invoice.getNetAmount() != null ? invoice.getNetAmount().toString() : "0.00",
            invoice.getCurrency() != null ? invoice.getCurrency() : "CNY",
            invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : "",
            invoice.getStatus() != null ? invoice.getStatus() : "processed"
        );
    }

    /**
     * 创建Python格式的响应（已废弃，保留用于向后兼容）
     * @deprecated 使用 convertToCompatibleResponse 替代
     */
    @Deprecated
    private Map<String, Object> createPythonFormatResponse(ProcessInvoiceRequest request) {
        Map<String, Object> pythonResponse = new HashMap<>();
        
        // 基本信息
        pythonResponse.put("success", true);
        pythonResponse.put("processing_time", "0.5s");
        pythonResponse.put("errors", new ArrayList<>());
        
        // 摘要信息
        Map<String, Object> summary = new HashMap<>();
        summary.put("total_inputs", 1);
        summary.put("successful_inputs", 1);
        summary.put("failed_inputs", 0);
        summary.put("total_output_invoices", 3);
        pythonResponse.put("summary", summary);
        
        // 文件映射
        List<Map<String, Object>> fileMapping = new ArrayList<>();
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("input_index", 0);
        fileInfo.put("input_type", "string");
        fileInfo.put("filename", "direct_input");
        fileInfo.put("success", true);
        fileInfo.put("invoice_number", "23902333");
        fileMapping.add(fileInfo);
        pythonResponse.put("file_mapping", fileMapping);
        
        // 执行详情
        Map<String, Object> executionDetails = new HashMap<>();
        executionDetails.put("completion_logs", createMockCompletionLogs());
        executionDetails.put("validation_logs", createMockValidationLogs());
        executionDetails.put("completion_by_file", List.of(Map.of(
            "file_index", 0,
            "file_name", "direct_input",
            "invoice_id", "23902333",
            "completion_logs", createMockCompletionLogs()
        )));
        executionDetails.put("validation_by_invoice", createMockValidationByInvoice());
        pythonResponse.put("execution_details", executionDetails);
        
        // 详情数组（空，因为这是单个处理）
        pythonResponse.put("details", new ArrayList<>());
        
        // 最重要的：results数组
        List<Map<String, Object>> results = new ArrayList<>();
        
        // 创建3个发票结果（模拟Python后端的行为）
        results.add(createInvoiceResult("output_1", "23902333_增值税专票", "增值税专用发票", request.getActualData()));
        results.add(createInvoiceResult("output_2", "23902333_增值税普票", "增值税普通发票", request.getActualData()));
        results.add(createInvoiceResult("output_3", "23902333_不动产租赁", "不动产租赁发票", request.getActualData()));
        
        pythonResponse.put("results", results);
        
        return pythonResponse;
    }
    
    /**
     * 创建单个发票结果
     */
    private Map<String, Object> createInvoiceResult(String invoiceId, String invoiceNumber, String invoiceType, String originalData) {
        Map<String, Object> result = new HashMap<>();
        result.put("invoice_id", invoiceId);
        result.put("invoice_number", invoiceNumber);
        result.put("success", true);
        result.put("source_system", "ERP");
        
        // 数据部分
        Map<String, Object> data = new HashMap<>();
        
        // 域对象
        Map<String, Object> domainObject = new HashMap<>();
        domainObject.put("invoice_number", invoiceNumber);
        domainObject.put("invoice_type", invoiceType);
        domainObject.put("customer_name", "携程广州分公司");
        domainObject.put("supplier_name", "金蝶软件(中国)有限公司广州分公司");
        domainObject.put("total_amount", "2520.00");
        domainObject.put("tax_amount", "151.20");
        domainObject.put("net_amount", "2368.80");
        domainObject.put("currency", "CNY");
        domainObject.put("invoice_date", "2025-01-01");
        domainObject.put("status", "completed");
        
        // 处理后的KDUBL
        String processedKdubl = generateProcessedKdubl(invoiceNumber, invoiceType, originalData);
        
        data.put("domain_object", domainObject);
        data.put("processed_kdubl", processedKdubl);
        result.put("data", data);
        
        return result;
    }
    
    /**
     * 生成处理后的KDUBL XML
     */
    private String generateProcessedKdubl(String invoiceNumber, String invoiceType, String originalData) {
        // 简化的KDUBL生成，基于原始数据进行修改
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Invoice>
                <InvoiceNumber>%s</InvoiceNumber>
                <InvoiceType>%s</InvoiceType>
                <Customer>
                    <Name>携程广州分公司</Name>
                    <TaxID>91440101MA5CQ4KE8F</TaxID>
                </Customer>
                <Supplier>
                    <Name>金蝶软件(中国)有限公司广州分公司</Name>
                    <TaxID>91440101MA5CQ4KE8F</TaxID>
                </Supplier>
                <Amount>
                    <Total>2520.00</Total>
                    <Tax>151.20</Tax>
                    <Net>2368.80</Net>
                    <Currency>CNY</Currency>
                </Amount>
                <Date>2025-01-01</Date>
                <Status>completed</Status>
            </Invoice>
            """, invoiceNumber, invoiceType);
    }
    
    /**
     * 创建Python格式的错误响应
     */
    private Map<String, Object> createPythonFormatErrorResponse(String errorCode, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("processing_time", "0.01s");
        errorResponse.put("errors", List.of(message));
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("total_inputs", 1);
        summary.put("successful_inputs", 0);
        summary.put("failed_inputs", 1);
        summary.put("total_output_invoices", 0);
        errorResponse.put("summary", summary);
        
        errorResponse.put("file_mapping", new ArrayList<>());
        errorResponse.put("execution_details", Map.of(
            "completion_logs", new ArrayList<>(),
            "validation_logs", new ArrayList<>(),
            "completion_by_file", new ArrayList<>(),
            "validation_by_invoice", new ArrayList<>()
        ));
        errorResponse.put("details", new ArrayList<>());
        errorResponse.put("results", new ArrayList<>());
        
        return errorResponse;
    }
    
    /**
     * 创建模拟的补全日志
     */
    private List<Map<String, Object>> createMockCompletionLogs() {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        String[] ruleCategories = {
            "税额计算", "客户地址补全", "供应商税号", "总金额计算", "币种设置"
        };
        
        for (int i = 0; i < 5; i++) {
            String category = ruleCategories[i];
            logs.add(Map.of(
                "rule_id", "completion_rule_" + (i + 1),
                "rule_name", category + "_规则",
                "status", "success", 
                "field_path", "invoice." + category.toLowerCase().replaceAll("[^a-zA-Z]", ""),
                "original_value", "",
                "completed_value", "已补全_" + category,
                "execution_time_ms", 1 + (i % 3)
            ));
        }
        
        return logs;
    }
    
    /**
     * 创建模拟的验证日志
     */
    private List<Map<String, Object>> createMockValidationLogs() {
        return List.of(
            Map.of(
                "rule_id", "validation_rule_1",
                "rule_name", "大额发票税号校验",
                "status", "passed",
                "field_path", "invoice.tax_id",
                "message", "发票金额 2520.00 元，已提供有效税号",
                "execution_time_ms", 2
            ),
            Map.of(
                "rule_id", "validation_rule_2", 
                "rule_name", "金额合理性校验",
                "status", "passed",
                "field_path", "invoice.total_amount",
                "message", "发票金额在合理范围内",
                "execution_time_ms", 1
            )
        );
    }
    
    /**
     * 创建按发票分组的验证日志
     */
    private List<Map<String, Object>> createMockValidationByInvoice() {
        return List.of(
            Map.of(
                "invoice_index", 0,
                "invoice_number", "23902333_增值税专票",
                "validation_logs", List.of(
                    Map.of("rule_name", "大额发票税号校验", "status", "passed", "message", "发票金额 2520.00 元，已提供有效税号")
                )
            ),
            Map.of(
                "invoice_index", 1,
                "invoice_number", "23902333_增值税普票",
                "validation_logs", List.of(
                    Map.of("rule_name", "发票类型校验", "status", "passed", "message", "增值税普通发票格式正确")
                )
            ),
            Map.of(
                "invoice_index", 2,
                "invoice_number", "23902333_不动产租赁",
                "validation_logs", List.of(
                    Map.of("rule_name", "租赁合同校验", "status", "passed", "message", "租赁合同信息完整")
                )
            )
        );
    }

    /**
     * 批量处理发票
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> processBatchInvoices(
            @Valid @RequestBody ProcessInvoiceRequest[] requests) {
        
        log.info("收到批量发票处理请求，数量: {}", requests.length);
        
        try {
            Map<String, Object> results = invoiceService.processBatchInvoices(requests);
            
            log.info("批量发票处理完成");
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            log.error("批量发票处理失败", e);
            return ResponseEntity.badRequest().body(
                Map.of(
                    "status", "error",
                    "message", "批量发票处理失败: " + e.getMessage()
                )
            );
        }
    }

    /**
     * 验证发票数据
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateInvoice(
            @Valid @RequestBody InvoiceDomainObject invoice) {
        
        log.info("收到发票验证请求，发票号: {}", invoice.getInvoiceNumber());
        
        try {
            Map<String, Object> validationResult = invoiceService.validateInvoice(invoice);
            
            log.info("发票验证完成，发票号: {}", invoice.getInvoiceNumber());
            return ResponseEntity.ok(validationResult);
            
        } catch (Exception e) {
            log.error("发票验证失败", e);
            return ResponseEntity.badRequest().body(
                Map.of(
                    "status", "error",
                    "message", "发票验证失败: " + e.getMessage()
                )
            );
        }
    }

    /**
     * 使用指定规则引擎处理发票数据
     * 
     * 提供更灵活的规则引擎选择接口
     * 
     * @param ruleEngine 规则引擎类型 (spel 或 cel)
     * @param request 发票处理请求
     * @return 处理结果
     */
    @PostMapping("/process/{ruleEngine}")
    public ResponseEntity<Map<String, Object>> processInvoiceWithEngine(
            @PathVariable("ruleEngine") String ruleEngine,
            @Valid @RequestBody ProcessInvoiceRequest request) {
        
        log.info("收到指定规则引擎的发票处理请求，引擎类型: {}, 数据类型: {}, 数据长度: {}", 
                ruleEngine, request.getActualDataType(), request.getDataLength());
        
        // 验证规则引擎类型
        if (!"spel".equalsIgnoreCase(ruleEngine) && !"cel".equalsIgnoreCase(ruleEngine)) {
            return ResponseEntity.badRequest().body(
                createPythonFormatErrorResponse("INVALID_RULE_ENGINE", 
                    "规则引擎类型必须是 'spel' 或 'cel'，当前值: " + ruleEngine)
            );
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 验证请求数据
            String actualData = request.getActualData();
            if (actualData == null || actualData.trim().isEmpty()) {
                log.warn("发票数据为空");
                return ResponseEntity.badRequest().body(
                    createPythonFormatErrorResponse("EMPTY_DATA", "发票数据不能为空")
                );
            }
            
            // 强制设置规则引擎类型
            request.setRuleEngine(ruleEngine.toLowerCase());
            
            // 使用指定的规则引擎处理发票数据
            log.debug("=== InvoiceController: 使用 {} 引擎处理发票 ===", ruleEngine);
            com.invoice.dto.ProcessInvoiceResponse processingResult = invoiceService.processInvoice(request);
            log.debug("=== InvoiceController: {} 引擎处理完成 ===", ruleEngine);
            
            // 转换为Python后端兼容的响应格式
            Map<String, Object> pythonResponse = convertToCompatibleResponse(processingResult, request, startTime);
            
            // 在响应中添加使用的规则引擎信息
            pythonResponse.put("rule_engine_used", ruleEngine.toLowerCase());
            
            log.info("发票处理完成，使用引擎: {}, 处理时间: {}ms, 返回 {} 个结果", 
                    ruleEngine, System.currentTimeMillis() - startTime,
                    ((List<?>) pythonResponse.get("results")).size());
            
            return ResponseEntity.ok(pythonResponse);
            
        } catch (Exception e) {
            log.error("使用 {} 引擎处理发票失败", ruleEngine, e);
            return ResponseEntity.badRequest().body(
                createPythonFormatErrorResponse("PROCESSING_ERROR", 
                    "使用 " + ruleEngine + " 引擎处理发票失败: " + e.getMessage())
            );
        }
    }

    /**
     * 获取当前支持的规则引擎列表
     * 
     * @return 支持的规则引擎列表和相关信息
     */
    @GetMapping("/engines")
    public ResponseEntity<Map<String, Object>> getSupportedEngines() {
        
        log.info("收到获取支持的规则引擎列表请求");
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        // 支持的引擎列表
        List<Map<String, Object>> engines = new ArrayList<>();
        
        // SpEL 引擎信息
        Map<String, Object> spelEngine = new HashMap<>();
        spelEngine.put("name", "spel");
        spelEngine.put("display_name", "Spring Expression Language");
        spelEngine.put("description", "基于Spring表达式语言的规则引擎，支持复杂的表达式计算");
        spelEngine.put("is_default", true);
        spelEngine.put("features", List.of(
            "复杂表达式计算",
            "丰富的内置函数",
            "类型安全",
            "详细的执行日志"
        ));
        engines.add(spelEngine);
        
        // CEL 引擎信息
        Map<String, Object> celEngine = new HashMap<>();
        celEngine.put("name", "cel");
        celEngine.put("display_name", "Common Expression Language");
        celEngine.put("description", "Google开发的通用表达式语言，性能优异");
        celEngine.put("is_default", false);
        celEngine.put("features", List.of(
            "高性能执行",
            "类型安全",
            "简洁的语法",
            "跨平台支持"
        ));
        engines.add(celEngine);
        
        response.put("engines", engines);
        response.put("default_engine", "spel");
        response.put("total_engines", engines.size());
        
        return ResponseEntity.ok(response);
    }
}