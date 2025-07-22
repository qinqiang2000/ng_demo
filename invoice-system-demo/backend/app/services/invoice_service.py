"""发票处理服务"""
from typing import Dict, List, Tuple
import yaml
from pathlib import Path
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule, FieldValidationRule
from ..core.kdubl_converter import KDUBLDomainConverter
from ..core.rule_engine import FieldCompletionEngine, BusinessValidationEngine
from ..core.cel_engine import DatabaseCELFieldCompletionEngine, DatabaseCELBusinessValidationEngine


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
            self.completion_engine = FieldCompletionEngine()
            self.validation_engine = BusinessValidationEngine()
        
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
        """处理KDUBL格式的发票数据"""
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
            
            # 3. 业务校验
            result["steps"].append("执行业务校验规则")
            if self.db_session:
                is_valid, errors = await self.validation_engine.validate_async(domain)
            else:
                is_valid, errors = self.validation_engine.validate(domain)
            # 收集校验执行日志
            result["execution_details"]["validation_logs"] = getattr(self.validation_engine, 'execution_log', [])
            
            if not is_valid:
                result["errors"] = errors
                result["steps"].append(f"✗ 校验失败: {len(errors)}个错误")
                return result
            
            result["steps"].append("✓ 业务校验通过")
            
            # 4. 转回KDUBL格式
            result["steps"].append("生成处理后的KDUBL")
            processed_kdubl = self.converter.build(domain)
            result["steps"].append("✓ KDUBL生成成功")
            
            # 5. 返回结果
            result["success"] = True
            result["data"] = {
                "domain_object": domain.dict(),
                "processed_kdubl": processed_kdubl,
                "source_system": source_system
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