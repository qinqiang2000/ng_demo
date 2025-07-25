"""发票处理服务"""
from typing import Dict, List, Tuple
import yaml
from pathlib import Path
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule, FieldValidationRule
from ..core.kdubl_converter import KDUBLDomainConverter
from ..core.rule_engine import FieldCompletionEngine, BusinessValidationEngine
from ..core.cel_engine import CELFieldCompletionEngine, CELBusinessValidationEngine, DatabaseCELFieldCompletionEngine, DatabaseCELBusinessValidationEngine
from .invoice_merge_service import InvoiceMergeService
from ..core.invoice_merge_engine import MergeStrategy


class InvoiceProcessingService:
    """发票处理服务"""
    
    def __init__(self, db_session: AsyncSession = None):
        self.converter = KDUBLDomainConverter()
        self.db_session = db_session
        
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
    
    async def process_kdubl_invoice(self, kdubl: str, source_system: str = "ERP") -> Dict:
        """处理KDUBL格式的发票数据（统一处理流程）"""
        result = {
            "success": False,
            "data": None,
            "errors": [],
            "steps": [],
            "execution_details": {
                "completion_logs": [],
                "validation_logs": []
            }
        }
        
        try:
            # 1. 解析为Domain Object
            result["steps"].append("解析KDUBL数据")
            domain = self.converter.parse(kdubl)
            result["steps"].append(f"✓ 成功解析发票: {domain.invoice_number}")
            
            # 2. 数据补全
            result["steps"].append("执行数据补全规则")
            if self.db_session:
                domain = await self.completion_engine.complete_async(domain)
            else:
                domain = self.completion_engine.complete(domain)
            # 收集补全执行日志
            result["execution_details"]["completion_logs"] = getattr(self.completion_engine, 'execution_log', [])
            result["steps"].append("✓ 数据补全完成")
            
            # 3. 合并拆分处理（单张发票作为长度为1的批次）
            result["steps"].append("执行合并拆分处理")
            merge_service = InvoiceMergeService()
            
            # 执行合并拆分一体化处理（对单张发票使用NONE策略）
            processed_invoices = merge_service.merge_and_split_invoices([domain], MergeStrategy.NONE)
            result["steps"].append(f"✓ 合并拆分完成，生成{len(processed_invoices)}张发票")
            
            # 4. 逐个校验和转换
            result["steps"].append("执行业务校验规则")
            results = []
            all_valid = True
            
            for i, invoice in enumerate(processed_invoices):
                # 业务校验
                if self.db_session:
                    is_valid, errors = await self.validation_engine.validate_async(invoice)
                else:
                    is_valid, errors = self.validation_engine.validate(invoice)
                
                if is_valid:
                    # 转换为KDUBL
                    processed_kdubl = self.converter.build(invoice)
                    
                    results.append({
                        "invoice_id": f"processed_{i+1}",
                        "invoice_number": invoice.invoice_number,
                        "success": True,
                        "domain_object": invoice.dict(),
                        "processed_kdubl": processed_kdubl,
                        "source_system": source_system
                    })
                else:
                    all_valid = False
                    results.append({
                        "invoice_id": f"processed_{i+1}",
                        "invoice_number": invoice.invoice_number,
                        "success": False,
                        "errors": errors,
                        "source_system": source_system
                    })
            
            # 收集校验执行日志
            result["execution_details"]["validation_logs"] = getattr(self.validation_engine, 'execution_log', [])
            
            if all_valid:
                result["steps"].append("✓ 业务校验通过")
            else:
                failed_count = len([r for r in results if not r["success"]])
                result["steps"].append(f"✗ 校验失败: {failed_count}张发票有错误")
            
            # 5. 返回结果
            result["success"] = True
            result["data"] = {
                "results": results,
                "summary": {
                    "total_processed": len(processed_invoices),
                    "successful_count": len([r for r in results if r["success"]]),
                    "failed_count": len([r for r in results if not r["success"]])
                }
            }
            
        except Exception as e:
            result["errors"].append(f"处理失败: {str(e)}")
            result["steps"].append(f"✗ 错误: {str(e)}")
        
        return result
    
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