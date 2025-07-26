"""规则管理服务"""
import yaml
import asyncio
from pathlib import Path
from typing import Dict, List, Optional, Any
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime

from ..models.domain import InvoiceDomainObject
from ..core.cel_evaluator import CELExpressionEvaluator, DatabaseCELExpressionEvaluator
from ..utils.logger import get_logger

logger = get_logger(__name__)


class RulesManagementService:
    """规则管理服务"""
    
    def __init__(self):
        self.config_path = Path(__file__).parent.parent.parent / "config" / "rules.yaml"
        self._ensure_config_exists()
    
    def _ensure_config_exists(self):
        """确保配置文件存在"""
        if not self.config_path.exists():
            # 创建默认配置
            default_config = {
                "field_completion_rules": [],
                "field_validation_rules": []
            }
            with open(self.config_path, 'w', encoding='utf-8') as f:
                yaml.safe_dump(default_config, f, default_flow_style=False, allow_unicode=True)
    
    async def get_all_rules(self) -> Dict[str, Any]:
        """获取所有规则"""
        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                config = yaml.safe_load(f)
            
            return {
                "completion_rules": config.get('field_completion_rules', []),
                "validation_rules": config.get('field_validation_rules', [])
            }
        except Exception as e:
            logger.error(f"读取规则配置失败: {e}")
            raise e
    
    async def get_completion_rules(self) -> List[Dict[str, Any]]:
        """获取补全规则"""
        rules = await self.get_all_rules()
        return rules["completion_rules"]
    
    async def get_validation_rules(self) -> List[Dict[str, Any]]:
        """获取校验规则"""
        rules = await self.get_all_rules()
        return rules["validation_rules"]
    
    async def get_completion_rule(self, rule_id: str) -> Optional[Dict[str, Any]]:
        """获取指定补全规则"""
        rules = await self.get_completion_rules()
        for rule in rules:
            if rule.get("id") == rule_id:
                return rule
        return None
    
    async def get_validation_rule(self, rule_id: str) -> Optional[Dict[str, Any]]:
        """获取指定校验规则"""
        rules = await self.get_validation_rules()
        for rule in rules:
            if rule.get("id") == rule_id:
                return rule
        return None
    
    async def create_completion_rule(self, rule_data: Dict[str, Any]) -> Dict[str, Any]:
        """创建补全规则"""
        try:
            config = await self._load_config()
            
            # 检查ID是否已存在
            existing_ids = [rule.get("id") for rule in config.get('field_completion_rules', [])]
            if rule_data["id"] in existing_ids:
                raise ValueError(f"规则ID已存在: {rule_data['id']}")
            
            # 添加新规则
            if 'field_completion_rules' not in config:
                config['field_completion_rules'] = []
            
            config['field_completion_rules'].append(rule_data)
            
            await self._save_config(config)
            
            logger.info(f"创建补全规则成功: {rule_data['id']}")
            return rule_data
            
        except Exception as e:
            logger.error(f"创建补全规则失败: {e}")
            raise e
    
    async def create_validation_rule(self, rule_data: Dict[str, Any]) -> Dict[str, Any]:
        """创建校验规则"""
        try:
            config = await self._load_config()
            
            # 检查ID是否已存在
            existing_ids = [rule.get("id") for rule in config.get('field_validation_rules', [])]
            if rule_data["id"] in existing_ids:
                raise ValueError(f"规则ID已存在: {rule_data['id']}")
            
            # 添加新规则
            if 'field_validation_rules' not in config:
                config['field_validation_rules'] = []
            
            config['field_validation_rules'].append(rule_data)
            
            await self._save_config(config)
            
            logger.info(f"创建校验规则成功: {rule_data['id']}")
            return rule_data
            
        except Exception as e:
            logger.error(f"创建校验规则失败: {e}")
            raise e
    
    async def update_completion_rule(self, rule_id: str, update_data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """更新补全规则"""
        try:
            config = await self._load_config()
            rules = config.get('field_completion_rules', [])
            
            # 查找并更新规则
            for i, rule in enumerate(rules):
                if rule.get("id") == rule_id:
                    # 更新字段
                    for key, value in update_data.items():
                        if value is not None:
                            rule[key] = value
                    
                    rules[i] = rule
                    config['field_completion_rules'] = rules
                    
                    await self._save_config(config)
                    
                    logger.info(f"更新补全规则成功: {rule_id}")
                    return rule
            
            return None
            
        except Exception as e:
            logger.error(f"更新补全规则失败: {e}")
            raise e
    
    async def update_validation_rule(self, rule_id: str, update_data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """更新校验规则"""
        try:
            config = await self._load_config()
            rules = config.get('field_validation_rules', [])
            
            # 查找并更新规则
            for i, rule in enumerate(rules):
                if rule.get("id") == rule_id:
                    # 更新字段
                    for key, value in update_data.items():
                        if value is not None:
                            rule[key] = value
                    
                    rules[i] = rule
                    config['field_validation_rules'] = rules
                    
                    await self._save_config(config)
                    
                    logger.info(f"更新校验规则成功: {rule_id}")
                    return rule
            
            return None
            
        except Exception as e:
            logger.error(f"更新校验规则失败: {e}")
            raise e
    
    async def delete_completion_rule(self, rule_id: str) -> bool:
        """删除补全规则"""
        try:
            config = await self._load_config()
            rules = config.get('field_completion_rules', [])
            
            # 查找并删除规则
            original_count = len(rules)
            rules = [rule for rule in rules if rule.get("id") != rule_id]
            
            if len(rules) < original_count:
                config['field_completion_rules'] = rules
                await self._save_config(config)
                
                logger.info(f"删除补全规则成功: {rule_id}")
                return True
            
            return False
            
        except Exception as e:
            logger.error(f"删除补全规则失败: {e}")
            raise e
    
    async def delete_validation_rule(self, rule_id: str) -> bool:
        """删除校验规则"""
        try:
            config = await self._load_config()
            rules = config.get('field_validation_rules', [])
            
            # 查找并删除规则
            original_count = len(rules)
            rules = [rule for rule in rules if rule.get("id") != rule_id]
            
            if len(rules) < original_count:
                config['field_validation_rules'] = rules
                await self._save_config(config)
                
                logger.info(f"删除校验规则成功: {rule_id}")
                return True
            
            return False
            
        except Exception as e:
            logger.error(f"删除校验规则失败: {e}")
            raise e
    
    async def reload_rules(self):
        """重新加载规则配置"""
        try:
            # 验证配置文件格式
            with open(self.config_path, 'r', encoding='utf-8') as f:
                config = yaml.safe_load(f)
            
            # 验证配置结构
            if not isinstance(config, dict):
                raise ValueError("配置文件格式错误：根对象必须是字典")
            
            if 'field_completion_rules' in config and not isinstance(config['field_completion_rules'], list):
                raise ValueError("field_completion_rules必须是数组")
            
            if 'field_validation_rules' in config and not isinstance(config['field_validation_rules'], list):
                raise ValueError("field_validation_rules必须是数组")
            
            # 通知全局的 invoice_service 重新加载规则
            # 需要导入并调用全局实例
            try:
                from ..main import invoice_service
                invoice_service._load_rules()
                logger.info("全局规则引擎重新加载成功")
            except Exception as e:
                logger.warning(f"无法重新加载全局规则引擎: {e}")
            
            logger.info("规则配置重新加载成功")
            
        except Exception as e:
            logger.error(f"重新加载规则配置失败: {e}")
            raise e
    
    async def validate_expression(self, expression: str, rule_type: str, db_session: AsyncSession = None, context_example: Dict[str, Any] = None) -> Dict[str, Any]:
        """验证表达式语法"""
        try:
            # 创建表达式评估器
            if db_session:
                evaluator = DatabaseCELExpressionEvaluator(db_session)
            else:
                evaluator = CELExpressionEvaluator()
            
            # 创建测试上下文
            test_context = context_example or self._create_test_context()
            
            # 尝试编译和执行表达式
            if db_session and ('db_query(' in expression or 'db.' in expression):
                # 异步执行包含数据库查询的表达式
                result = await evaluator.evaluate_async(expression, test_context)
            else:
                # 同步执行普通表达式
                result = evaluator.evaluate(expression, test_context)
            
            return {
                "valid": True,
                "result": str(result),
                "result_type": type(result).__name__,
                "expression": expression,
                "context_used": list(test_context.keys())
            }
            
        except Exception as e:
            return {
                "valid": False,
                "error": str(e),
                "expression": expression,
                "suggestion": self._get_expression_suggestion(expression, str(e))
            }
    
    async def get_domain_fields(self) -> Dict[str, Any]:
        """获取可用的领域对象字段"""
        return {
            "invoice": {
                "description": "发票主对象",
                "fields": {
                    "invoice_number": {"type": "string", "description": "发票号码"},
                    "issue_date": {"type": "date", "description": "开票日期"},
                    "invoice_type": {"type": "string", "description": "发票类型"},
                    "country": {"type": "string", "description": "国家"},
                    "total_amount": {"type": "decimal", "description": "总金额"},
                    "tax_amount": {"type": "decimal", "description": "税额"},
                    "net_amount": {"type": "decimal", "description": "净额"},
                    "supplier": {
                        "type": "object",
                        "description": "供应商信息",
                        "fields": {
                            "name": {"type": "string", "description": "供应商名称"},
                            "tax_no": {"type": "string", "description": "供应商税号"},
                            "email": {"type": "string", "description": "供应商邮箱"},
                            "phone": {"type": "string", "description": "供应商电话"}
                        }
                    },
                    "customer": {
                        "type": "object", 
                        "description": "客户信息",
                        "fields": {
                            "name": {"type": "string", "description": "客户名称"},
                            "tax_no": {"type": "string", "description": "客户税号"},
                            "email": {"type": "string", "description": "客户邮箱"},
                            "phone": {"type": "string", "description": "客户电话"}
                        }
                    },
                    "items": {
                        "type": "array",
                        "description": "发票项目列表",
                        "item_fields": {
                            "item_id": {"type": "string", "description": "项目ID"},
                            "description": {"type": "string", "description": "项目描述"},
                            "name": {"type": "string", "description": "标准商品名称"},
                            "quantity": {"type": "decimal", "description": "数量"},
                            "unit_price": {"type": "decimal", "description": "单价"},
                            "amount": {"type": "decimal", "description": "金额"},
                            "tax_rate": {"type": "decimal", "description": "税率"},
                            "tax_amount": {"type": "decimal", "description": "税额"}
                        }
                    },
                    "extensions": {
                        "type": "object",
                        "description": "扩展字段",
                        "fields": {
                            "supplier_category": {"type": "string", "description": "供应商分类"},
                            "invoice_type": {"type": "string", "description": "发票类型"},
                            "total_quantity": {"type": "decimal", "description": "总数量"}
                        }
                    }
                }
            }
        }
    
    async def get_available_functions(self) -> Dict[str, Any]:
        """获取可用的函数列表"""
        return {
            "cel_builtin": {
                "description": "CEL内置函数",
                "functions": {
                    "has(field)": {
                        "description": "检查字段是否存在且不为null",
                        "example": "has(invoice.tax_amount)",
                        "returns": "boolean"
                    },
                    "size()": {
                        "description": "获取数组或字符串长度",
                        "example": "invoice.items.size()",
                        "returns": "int"
                    },
                    "matches(pattern)": {
                        "description": "正则表达式匹配",
                        "example": "invoice.supplier.tax_no.matches('^[0-9]{15}[A-Z0-9]{3}$')",
                        "returns": "boolean"
                    },
                    "map(var, expr)": {
                        "description": "数组映射操作",
                        "example": "invoice.items.map(item, item.amount)",
                        "returns": "array"
                    },
                    "filter(var, expr)": {
                        "description": "数组过滤操作",
                        "example": "invoice.items.filter(item, item.amount > 100)",
                        "returns": "array"
                    },
                    "all(var, expr)": {
                        "description": "检查数组所有元素是否满足条件",
                        "example": "invoice.items.all(item, item.amount > 0)",
                        "returns": "boolean"
                    },
                    "exists(var, expr)": {
                        "description": "检查数组是否存在满足条件的元素",
                        "example": "invoice.items.exists(item, item.tax_rate > 0.1)",
                        "returns": "boolean"
                    }
                }
            },
            "custom_functions": {
                "description": "自定义函数",
                "functions": {
                    "db.table.field[conditions]": {
                        "description": "极简数据库查询语法（推荐）",
                        "example": "db.companies.tax_number[name=invoice.supplier.name]",
                        "returns": "查询结果值",
                        "note": "自动限制返回一条记录"
                    },
                    "db_query(query_name, ...params)": {
                        "description": "数据库查询函数（旧语法，向后兼容）",
                        "example": "db_query('get_tax_number_by_name', invoice.supplier.name)",
                        "returns": "any",
                        "note": "建议使用新的db.table.field语法"
                    },
                    "get_standard_name(description)": {
                        "description": "获取标准商品名称",
                        "example": "get_standard_name(item.description)",
                        "returns": "string"
                    },
                    "get_tax_rate(description)": {
                        "description": "获取商品税率",
                        "example": "get_tax_rate(item.description)",
                        "returns": "decimal"
                    },
                    "get_tax_category(description)": {
                        "description": "获取商品税种",
                        "example": "get_tax_category(item.description)",
                        "returns": "string"
                    }
                }
            },
            "operators": {
                "description": "操作符",
                "list": {
                    "==": "等于",
                    "!=": "不等于", 
                    ">": "大于",
                    ">=": "大于等于",
                    "<": "小于",
                    "<=": "小于等于",
                    "&&": "逻辑与",
                    "||": "逻辑或",
                    "!": "逻辑非",
                    "+": "加法",
                    "-": "减法",
                    "*": "乘法",
                    "/": "除法",
                    "%": "取模"
                }
            },
            "database_query_syntax": {
                "description": "数据库查询语法示例",
                "examples": {
                    "单条件查询": "db.companies.tax_number[name=invoice.supplier.name]",
                    "多条件查询": "db.tax_rates.rate[category=$category, amount>=$total]",
                    "查询所有字段": "db.companies[name='携程广州']",
                    "使用变量": "db.companies.category[name=$supplier_name]",
                    "默认值处理": "db.companies.category[name=$name] or 'GENERAL'"
                },
                "supported_tables": {
                    "companies": ["name", "tax_number", "category", "address", "email"],
                    "tax_rates": ["category", "rate", "min_amount", "max_amount"]
                }
            }
        }
    
    def _create_test_context(self) -> Dict[str, Any]:
        """创建测试上下文"""
        from decimal import Decimal
        from datetime import date
        
        return {
            "invoice": {
                "invoice_number": "INV-2024-001",
                "issue_date": "2024-01-15",
                "invoice_type": "NORMAL",
                "country": "CN",
                "total_amount": Decimal("1000.00"),
                "tax_amount": Decimal("60.00"),
                "net_amount": Decimal("940.00"),
                "supplier": {
                    "name": "测试供应商",
                    "tax_no": "123456789012345678",
                    "email": "supplier@test.com",
                    "phone": "13800138000"
                },
                "customer": {
                    "name": "测试客户",
                    "tax_no": "987654321098765432",
                    "email": "customer@test.com",
                    "phone": "13900139000"
                },
                "items": [
                    {
                        "item_id": "1",
                        "description": "测试商品1",
                        "name": "标准商品1",
                        "quantity": Decimal("2"),
                        "unit_price": Decimal("300.00"),
                        "amount": Decimal("600.00"),
                        "tax_rate": Decimal("0.06"),
                        "tax_amount": Decimal("36.00")
                    },
                    {
                        "item_id": "2", 
                        "description": "测试商品2",
                        "name": "标准商品2",
                        "quantity": Decimal("1"),
                        "unit_price": Decimal("400.00"),
                        "amount": Decimal("400.00"),
                        "tax_rate": Decimal("0.06"),
                        "tax_amount": Decimal("24.00")
                    }
                ],
                "extensions": {
                    "supplier_category": "GENERAL",
                    "invoice_type": "NORMAL",
                    "total_quantity": Decimal("3")
                }
            }
        }
    
    def _get_expression_suggestion(self, expression: str, error: str) -> str:
        """根据错误信息提供表达式建议"""
        suggestions = []
        
        if "field" in error.lower() or "attribute" in error.lower():
            suggestions.append("检查字段路径是否正确，如 invoice.supplier.name")
        
        if "syntax" in error.lower():
            suggestions.append("检查表达式语法，确保括号匹配")
        
        if "function" in error.lower():
            suggestions.append("检查函数名称和参数是否正确")
        
        if "type" in error.lower():
            suggestions.append("检查数据类型是否匹配")
        
        return "建议：" + "；".join(suggestions) if suggestions else ""
    
    async def _load_config(self) -> Dict[str, Any]:
        """加载配置文件"""
        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                return yaml.safe_load(f) or {}
        except Exception as e:
            logger.error(f"加载配置文件失败: {e}")
            raise e
    
    async def _save_config(self, config: Dict[str, Any]):
        """保存配置文件""" 
        try:
            # 备份原文件
            backup_path = self.config_path.with_suffix('.yaml.backup')
            if self.config_path.exists():
                import shutil
                shutil.copy2(self.config_path, backup_path)
            
            # 保存新配置
            with open(self.config_path, 'w', encoding='utf-8') as f:
                yaml.safe_dump(config, f, default_flow_style=False, allow_unicode=True, indent=2)
            
        except Exception as e:
            logger.error(f"保存配置文件失败: {e}")
            raise e