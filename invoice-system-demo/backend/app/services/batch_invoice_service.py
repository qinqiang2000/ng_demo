"""批量发票处理服务"""
import asyncio
from typing import List, Dict, Any, Optional
from fastapi import UploadFile
from sqlalchemy.ext.asyncio import AsyncSession
import uuid
from datetime import datetime

from .invoice_service import InvoiceProcessingService
from .invoice_merge_service import InvoiceMergeService
from ..core.invoice_merge_engine import MergeStrategy
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
        """批量处理发票（复用单张处理逻辑）
        
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
            "results": [],
            "errors": [],
            "summary": {
                "total_files": len(xml_files),
                "successful_files": 0,
                "failed_files": 0,
                "total_output_invoices": 0
            }
        }
        
        try:
            # 批量处理：对每个文件调用单张处理逻辑
            successful_files = 0
            failed_files = 0
            total_output_invoices = 0
            
            # 收集所有成功处理的发票，用于统一合并拆分
            all_completed_invoices = []
            file_invoice_mapping = []  # 记录文件与发票的映射关系
            
            # 第一阶段：逐个处理文件（解析+补全）
            for i, file in enumerate(xml_files):
                try:
                    # 读取文件内容
                    content = await file.read()
                    kdubl_xml = content.decode('utf-8')
                    await file.seek(0)  # 重置文件指针
                    
                    # 调用单张处理的前两个步骤（解析+补全）
                    domain_obj = self.converter.parse(kdubl_xml)
                    
                    # 数据补全
                    if self.db_session:
                        domain_obj = await self.invoice_service.completion_engine.complete_async(domain_obj)
                    else:
                        domain_obj = self.invoice_service.completion_engine.complete(domain_obj)
                    
                    all_completed_invoices.append(domain_obj)
                    file_invoice_mapping.append({
                        "file_index": i,
                        "filename": file.filename,
                        "invoice_number": domain_obj.invoice_number,
                        "success": True
                    })
                    successful_files += 1
                    
                except Exception as e:
                    file_invoice_mapping.append({
                        "file_index": i,
                        "filename": file.filename,
                        "success": False,
                        "error": str(e)
                    })
                    failed_files += 1
            
            # 第二阶段：统一合并拆分（批量一次性处理）
            if all_completed_invoices:
                # 转换合并策略
                try:
                    strategy_enum = MergeStrategy(merge_strategy)
                except ValueError:
                    strategy_enum = MergeStrategy.NONE
                
                # 执行合并拆分一体化处理
                processed_invoices = self.merge_service.merge_and_split_invoices(
                    all_completed_invoices, 
                    strategy_enum,
                    merge_config=merge_config
                )
                
                total_output_invoices = len(processed_invoices)
                
                # 第三阶段：逐个校验和转换
                for i, invoice in enumerate(processed_invoices):
                    try:
                        # 业务校验
                        if self.db_session:
                            is_valid, errors = await self.invoice_service.validation_engine.validate_async(invoice)
                        else:
                            is_valid, errors = self.invoice_service.validation_engine.validate(invoice)
                        
                        # 收集校验执行日志
                        validation_logs = getattr(self.invoice_service.validation_engine, 'execution_log', [])
                        
                        if is_valid:
                            # 转换为KDUBL
                            processed_kdubl = self.converter.build(invoice)
                            result["results"].append({
                                "invoice_id": f"output_{i+1}",
                                "invoice_number": invoice.invoice_number,
                                "success": True,
                                "data": {
                                    "domain_object": invoice.dict(),
                                    "processed_kdubl": processed_kdubl
                                },
                                "validation_logs": validation_logs
                            })
                        else:
                            result["results"].append({
                                "invoice_id": f"output_{i+1}",
                                "invoice_number": invoice.invoice_number,
                                "success": False,
                                "errors": errors,
                                "validation_logs": validation_logs
                            })
                    except Exception as e:
                        result["results"].append({
                            "invoice_id": f"output_{i+1}",
                            "invoice_number": getattr(invoice, 'invoice_number', 'unknown'),
                            "success": False,
                            "errors": [f"处理异常: {str(e)}"]
                        })
            
            # 更新摘要信息
            result["summary"].update({
                "successful_files": successful_files,
                "failed_files": failed_files,
                "total_output_invoices": total_output_invoices,
                "file_mapping": file_invoice_mapping
            })
            
            # 判断整体成功状态
            result["success"] = failed_files == 0 and all(
                r["success"] for r in result["results"]
            )
                
        except Exception as e:
            result["errors"].append(f"批量处理异常: {str(e)}")
        
        # 计算处理时间
        end_time = datetime.now()
        processing_time = (end_time - start_time).total_seconds()
        result["processing_time"] = f"{processing_time:.2f}s"
        
        return result