"""批量发票处理服务"""
import asyncio
from typing import List, Dict, Any, Optional
from fastapi import UploadFile
from sqlalchemy.ext.asyncio import AsyncSession
import uuid
from datetime import datetime

from .invoice_service import InvoiceProcessingService
from .invoice_merge_service import InvoiceMergeService, MergeStrategy
from ..models.domain import InvoiceDomainObject
from ..core.kdubl_converter import KDUBLDomainConverter


class BatchInvoiceProcessingService:
    """批量发票处理服务"""
    
    def __init__(self, db_session: AsyncSession = None):
        self.db_session = db_session
        self.invoice_service = InvoiceProcessingService(db_session)
        self.merge_service = InvoiceMergeService()
        self.converter = KDUBLDomainConverter()
        
    async def process_batch_invoices(
        self, 
        xml_files: List[UploadFile], 
        source_system: str = "ERP",
        merge_strategy: str = "none",
        merge_config: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """批量处理发票
        
        Args:
            xml_files: XML文件列表
            source_system: 来源系统
            merge_strategy: 合并策略
            merge_config: 合并配置
            
        Returns:
            批量处理结果
        """
        batch_id = f"batch_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"
        start_time = datetime.now()
        
        result = {
            "success": False,
            "batch_id": batch_id,
            "total_files": len(xml_files),
            "processing_time": None,
            "stages": {
                "completion": {
                    "total_files": len(xml_files),
                    "successful_count": 0,
                    "failed_count": 0,
                    "details": []
                },
                "merge": {
                    "input_invoices": 0,
                    "output_invoices": 0,
                    "merge_strategy": merge_strategy,
                    "execution_log": []
                },
                "validation": {
                    "total_invoices": 0,
                    "passed_count": 0,
                    "failed_count": 0,
                    "details": []
                }
            },
            "results": [],
            "errors": []
        }
        
        try:
            # 阶段1：批量补全
            completion_results = await self._batch_completion(xml_files, source_system)
            result["stages"]["completion"] = completion_results["stage_info"]
            
            if completion_results["successful_invoices"]:
                # 阶段2：合并拆分
                merge_results = await self._batch_merge(
                    completion_results["successful_invoices"], 
                    merge_strategy, 
                    merge_config
                )
                result["stages"]["merge"] = merge_results["stage_info"]
                
                # 阶段3：批量校验和转换
                validation_results = await self._batch_validation_and_conversion(
                    merge_results["merged_invoices"]
                )
                result["stages"]["validation"] = validation_results["stage_info"]
                result["results"] = validation_results["results"]
                
                # 判断整体成功状态
                result["success"] = validation_results["stage_info"]["failed_count"] == 0
            else:
                result["errors"].append("所有文件补全失败，无法进行后续处理")
                
        except Exception as e:
            result["errors"].append(f"批量处理异常: {str(e)}")
        
        # 计算处理时间
        end_time = datetime.now()
        processing_time = (end_time - start_time).total_seconds()
        result["processing_time"] = f"{processing_time:.2f}s"
        
        return result
    
    async def _batch_completion(
        self, 
        xml_files: List[UploadFile], 
        source_system: str
    ) -> Dict[str, Any]:
        """批量补全阶段"""
        successful_invoices = []
        completion_details = []
        
        # 并发处理所有文件
        tasks = []
        for file in xml_files:
            task = self._process_single_file_completion(file, source_system)
            tasks.append(task)
        
        # 等待所有任务完成
        completion_results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # 处理结果
        successful_count = 0
        failed_count = 0
        
        for i, result in enumerate(completion_results):
            filename = xml_files[i].filename
            
            if isinstance(result, Exception):
                # 处理异常
                completion_details.append({
                    "filename": filename,
                    "success": False,
                    "error": str(result)
                })
                failed_count += 1
            elif result["success"]:
                # 处理成功
                domain_obj = result["domain_object"]
                successful_invoices.append(domain_obj)
                completion_details.append({
                    "filename": filename,
                    "success": True,
                    "invoice_number": domain_obj.invoice_number,
                    "steps": result["steps"]
                })
                successful_count += 1
            else:
                # 处理失败
                completion_details.append({
                    "filename": filename,
                    "success": False,
                    "errors": result["errors"]
                })
                failed_count += 1
        
        return {
            "successful_invoices": successful_invoices,
            "stage_info": {
                "total_files": len(xml_files),
                "successful_count": successful_count,
                "failed_count": failed_count,
                "details": completion_details
            }
        }
    
    async def _process_single_file_completion(
        self, 
        file: UploadFile, 
        source_system: str
    ) -> Dict[str, Any]:
        """处理单个文件的补全（仅执行解析和补全，不包括校验）"""
        try:
            # 读取文件内容
            content = await file.read()
            kdubl_xml = content.decode('utf-8')
            
            # 重置文件指针（如果需要再次读取）
            await file.seek(0)
            
            # 1. 解析为Domain Object
            domain_obj = self.converter.parse(kdubl_xml)
            
            # 2. 仅执行数据补全（不执行校验）
            if self.db_session:
                domain_obj = await self.invoice_service.completion_engine.complete_async(domain_obj)
            else:
                domain_obj = self.invoice_service.completion_engine.complete(domain_obj)
            
            return {
                "success": True,
                "domain_object": domain_obj,
                "steps": [f"✓ 成功解析发票: {domain_obj.invoice_number}", "✓ 数据补全完成"]
            }
                
        except Exception as e:
            return {
                "success": False,
                "errors": [f"文件处理异常: {str(e)}"]
            }
    
    async def _batch_merge(
        self, 
        completed_invoices: List[InvoiceDomainObject], 
        merge_strategy: str,
        merge_config: Optional[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """批量合并阶段"""
        original_count = len(completed_invoices)
        
        try:
            # 转换合并策略
            strategy = MergeStrategy(merge_strategy)
        except ValueError:
            strategy = MergeStrategy.NONE
        
        # 执行合并
        merged_invoices = self.merge_service.merge_invoices(
            completed_invoices, 
            strategy, 
            merge_config
        )
        
        merged_count = len(merged_invoices)
        
        return {
            "merged_invoices": merged_invoices,
            "stage_info": {
                "input_invoices": original_count,
                "output_invoices": merged_count,
                "merge_strategy": merge_strategy,
                "execution_log": self.merge_service.execution_log
            }
        }
    
    async def _batch_validation_and_conversion(
        self, 
        merged_invoices: List[InvoiceDomainObject]
    ) -> Dict[str, Any]:
        """批量校验和转换阶段"""
        validation_details = []
        final_results = []
        passed_count = 0
        failed_count = 0
        
        for i, invoice in enumerate(merged_invoices):
            try:
                # 业务校验
                if self.db_session:
                    is_valid, errors = await self.invoice_service.validation_engine.validate_async(invoice)
                else:
                    is_valid, errors = self.invoice_service.validation_engine.validate(invoice)
                
                if is_valid:
                    # 转换为KDUBL
                    processed_kdubl = self.converter.build(invoice)
                    
                    final_results.append({
                        "invoice_id": f"merged_{i+1}",
                        "invoice_number": invoice.invoice_number,
                        "success": True,
                        "data": {
                            "domain_object": invoice.dict(),
                            "processed_kdubl": processed_kdubl
                        }
                    })
                    
                    validation_details.append({
                        "invoice_number": invoice.invoice_number,
                        "success": True,
                        "validation_passed": True
                    })
                    passed_count += 1
                else:
                    final_results.append({
                        "invoice_id": f"merged_{i+1}",
                        "invoice_number": invoice.invoice_number,
                        "success": False,
                        "errors": errors
                    })
                    
                    validation_details.append({
                        "invoice_number": invoice.invoice_number,
                        "success": False,
                        "validation_passed": False,
                        "errors": errors
                    })
                    failed_count += 1
                    
            except Exception as e:
                final_results.append({
                    "invoice_id": f"merged_{i+1}",
                    "invoice_number": getattr(invoice, 'invoice_number', 'unknown'),
                    "success": False,
                    "errors": [f"处理异常: {str(e)}"]
                })
                
                validation_details.append({
                    "invoice_number": getattr(invoice, 'invoice_number', 'unknown'),
                    "success": False,
                    "validation_passed": False,
                    "errors": [f"处理异常: {str(e)}"]
                })
                failed_count += 1
        
        return {
            "results": final_results,
            "stage_info": {
                "total_invoices": len(merged_invoices),
                "passed_count": passed_count,
                "failed_count": failed_count,
                "details": validation_details
            }
        }