"""统一发票处理服务 - 支持单张和批量处理"""
import asyncio
from typing import Dict, List, Tuple, Optional, Any, Union
import yaml
from pathlib import Path
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import UploadFile
import uuid
from datetime import datetime

from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule, FieldValidationRule
from ..core.kdubl_converter import KDUBLDomainConverter
from ..core.rule_engine import FieldCompletionEngine, BusinessValidationEngine
from ..core.cel_engine import CELFieldCompletionEngine, CELBusinessValidationEngine, DatabaseCELFieldCompletionEngine, DatabaseCELBusinessValidationEngine
from .invoice_merge_service import InvoiceMergeService
from ..core.invoice_merge_engine import MergeStrategy


class InvoiceProcessingService:
    """统一发票处理服务 - 支持单张和批量处理"""
    
    def __init__(self, db_session: AsyncSession = None):
        self.converter = KDUBLDomainConverter()
        self.db_session = db_session
        self.merge_service = InvoiceMergeService()
        
        # 如果有数据库会话，使用支持数据库查询的CEL引擎
        if db_session:
            self.completion_engine = DatabaseCELFieldCompletionEngine(db_session)
            self.validation_engine = DatabaseCELBusinessValidationEngine(db_session)
        else:
            self.completion_engine = CELFieldCompletionEngine()
            self.validation_engine = CELBusinessValidationEngine()
        
        self._load_rules()
    
    def _load_rules(self):
        """从配置文件加载规则"""
        config_path = Path(__file__).parent.parent.parent / "config" / "rules.yaml"
        if not config_path.exists():
            print(f"规则配置文件不存在: {config_path}")
            return
        
        with open(config_path, 'r', encoding='utf-8') as f:
            config = yaml.safe_load(f)
        
        # 加载补全规则
        completion_rules = []
        for rule_data in config.get('field_completion_rules', []):
            rule = FieldCompletionRule(**rule_data)
            completion_rules.append(rule)
        self.completion_engine.load_rules(completion_rules)
        
        # 加载校验规则
        validation_rules = []
        for rule_data in config.get('field_validation_rules', []):
            rule = FieldValidationRule(**rule_data)
            validation_rules.append(rule)
        self.validation_engine.load_rules(validation_rules)

    async def process_invoices(
        self, 
        inputs: Union[List[UploadFile], List[str], str],
        source_system: str = "ERP",
        merge_strategy: str = "none",
        merge_config: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """统一发票处理接口 - 支持单张和批量处理
        
        Args:
            inputs: 输入数据，可以是：
                   - List[UploadFile]: 上传的文件列表（批量）
                   - List[str]: KDUBL XML字符串列表（批量）
                   - str: 单个KDUBL XML字符串（单张）
            source_system: 来源系统
            merge_strategy: 合并策略
            merge_config: 合并配置
            
        Returns:
            统一的处理结果格式
        """
        # 生成处理批次ID
        batch_id = f"batch_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"
        start_time = datetime.now()
        
        # 标准化输入为列表格式
        kdubl_list, file_mapping = await self._normalize_inputs(inputs)
        
        result = {
            "success": False,
            "batch_id": batch_id,
            "total_inputs": len(kdubl_list),
            "processing_time": None,
            "results": [],
            "errors": [],
            "summary": {
                "total_inputs": len(kdubl_list),
                "successful_inputs": 0,
                "failed_inputs": 0,
                "total_output_invoices": 0
            },
            "file_mapping": file_mapping,
            "execution_details": {
                "completion_logs": [],
                "validation_logs": []
            }
        }
        
        try:
            # 第一阶段：解析和补全
            all_completed_invoices = []
            successful_inputs = 0
            failed_inputs = 0
            
            for i, kdubl_xml in enumerate(kdubl_list):
                print("="*19, f"正在处理第 {i+1} 个XML文档", "="*19)
                try:
                    # 解析为Domain Object
                    domain_obj = self.converter.parse(kdubl_xml)
                    
                    # 数据补全
                    if self.db_session:
                        domain_obj = await self.completion_engine.complete_async(domain_obj)
                    else:
                        domain_obj = self.completion_engine.complete(domain_obj)
                    
                    all_completed_invoices.append(domain_obj)
                    successful_inputs += 1
                    
                    # 更新文件映射状态
                    if i < len(file_mapping):
                        file_mapping[i]["success"] = True
                        file_mapping[i]["invoice_number"] = domain_obj.invoice_number
                    
                except Exception as e:
                    failed_inputs += 1
                    if i < len(file_mapping):
                        file_mapping[i]["success"] = False
                        file_mapping[i]["error"] = str(e)
            
            # 收集补全执行日志
            result["execution_details"]["completion_logs"] = getattr(self.completion_engine, 'execution_log', [])
            
            # 第二阶段：合并拆分处理
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
                
                # 第三阶段：校验和转换
                for i, invoice in enumerate(processed_invoices):
                    try:
                        # 业务校验
                        if self.db_session:
                            is_valid, errors = await self.validation_engine.validate_async(invoice)
                        else:
                            is_valid, errors = self.validation_engine.validate(invoice)
                        
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
                                "source_system": source_system
                            })
                        else:
                            result["results"].append({
                                "invoice_id": f"output_{i+1}",
                                "invoice_number": invoice.invoice_number,
                                "success": False,
                                "errors": errors,
                                "source_system": source_system
                            })
                    except Exception as e:
                        result["results"].append({
                            "invoice_id": f"output_{i+1}",
                            "invoice_number": getattr(invoice, 'invoice_number', 'unknown'),
                            "success": False,
                            "errors": [f"处理异常: {str(e)}"]
                        })
                
                # 收集校验执行日志
                result["execution_details"]["validation_logs"] = getattr(self.validation_engine, 'execution_log', [])
            
            # 更新摘要信息
            result["summary"].update({
                "successful_inputs": successful_inputs,
                "failed_inputs": failed_inputs,
                "total_output_invoices": len(result["results"])
            })
            
            # 判断整体成功状态
            result["success"] = failed_inputs == 0 and all(
                r["success"] for r in result["results"]
            )
                
        except Exception as e:
            result["errors"].append(f"处理异常: {str(e)}")
        
        # 计算处理时间
        end_time = datetime.now()
        processing_time = (end_time - start_time).total_seconds()
        result["processing_time"] = f"{processing_time:.2f}s"
        
        return result

    async def _normalize_inputs(
        self, 
        inputs: Union[List[UploadFile], List[str], str]
    ) -> Tuple[List[str], List[Dict[str, Any]]]:
        """标准化输入为KDUBL字符串列表和文件映射"""
        kdubl_list = []
        file_mapping = []
        
        if isinstance(inputs, str):
            # 单个字符串
            kdubl_list = [inputs]
            file_mapping = [{
                "input_index": 0,
                "input_type": "string",
                "filename": "direct_input",
                "success": None
            }]
        elif isinstance(inputs, list):
            if len(inputs) > 0 and hasattr(inputs[0], 'read'):
                # UploadFile列表
                for i, file in enumerate(inputs):
                    try:
                        content = await file.read()
                        kdubl_xml = content.decode('utf-8')
                        await file.seek(0)  # 重置文件指针
                        kdubl_list.append(kdubl_xml)
                        file_mapping.append({
                            "input_index": i,
                            "input_type": "file",
                            "filename": file.filename,
                            "success": None
                        })
                    except Exception as e:
                        file_mapping.append({
                            "input_index": i,
                            "input_type": "file",
                            "filename": getattr(file, 'filename', f'file_{i}'),
                            "success": False,
                            "error": f"文件读取失败: {str(e)}"
                        })
            else:
                # 字符串列表
                kdubl_list = inputs
                file_mapping = [{
                    "input_index": i,
                    "input_type": "string",
                    "filename": f"input_{i+1}",
                    "success": None
                } for i in range(len(inputs))]
        
        return kdubl_list, file_mapping

    def get_loaded_rules(self) -> Dict[str, List]:
        """获取已加载的规则"""
        return {
            "completion_rules": [
                {
                    "id": rule.id,
                    "name": rule.rule_name,
                    "active": rule.active,
                    "priority": rule.priority,
                    "apply_to": rule.apply_to,
                    "target_field": rule.target_field
                }
                for rule in self.completion_engine.rules
            ],
            "validation_rules": [
                {
                    "id": rule.id,
                    "name": rule.rule_name,
                    "active": rule.active,
                    "priority": rule.priority,
                    "apply_to": rule.apply_to,
                    "field_path": rule.field_path,
                    "error_message": rule.error_message
                }
                for rule in self.validation_engine.rules
            ]
        }