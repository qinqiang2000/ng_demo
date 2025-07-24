"""CEL字段补全引擎模块

本模块包含基于CEL的字段补全功能，包括：
- 基础字段补全引擎
- 支持数据库查询的字段补全引擎
- items[]语法处理
- 规则加载和执行
- 执行日志记录
"""

from typing import Any, Dict, List
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule
from .cel_evaluator import CELExpressionEvaluator, DatabaseCELExpressionEvaluator
from ..utils.logger import get_logger

# 创建logger
logger = get_logger(__name__)


class CELFieldCompletionEngine:
    """基于CEL的字段补全引擎"""
    
    def __init__(self):
        self.evaluator = CELExpressionEvaluator()
        self.rules: List = []
        self.execution_log: List[Dict[str, Any]] = []
        # 自动加载规则
        self._load_rules_from_config()
    
    def _load_rules_from_config(self):
        """从配置文件加载规则"""
        import yaml
        import os
        
        config_path = os.path.join(os.path.dirname(__file__), '../../config/rules.yaml')
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config = yaml.safe_load(f)
            
            rules = []
            for rule_data in config.get('field_completion_rules', []):
                rule = FieldCompletionRule(**rule_data)
                rules.append(rule)
            
            self.load_rules(rules)
            logger.debug(f"成功加载 {len(rules)} 条规则")
            
        except Exception as e:
            logger.debug(f"加载规则配置失败: {str(e)}")
    
    def load_rules(self, rules: List):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def complete(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """执行字段补全"""
        self.execution_log = []  # 重置日志
        context = {'invoice': domain}
        
        logger.debug(f"CEL字段补全开始，共有 {len(self.rules)} 条规则")
        
        for rule in self.rules:
            logger.debug(f"处理规则: {rule.rule_name}, target_field: '{rule.target_field}', active: {rule.active}")
            
            if not rule.active:
                logger.debug(f"规则 {rule.rule_name} 未激活，跳过")
                continue
            
            # 检查是否是items[]语法
            if rule.target_field.startswith('items[].'):
                logger.debug(f"识别为items[]规则: {rule.rule_name}")
                self._process_items_rule(rule, domain)
            else:
                logger.debug(f"识别为普通规则: {rule.rule_name}")
                # 原有的CEL处理逻辑
                try:
                    # 检查应用条件
                    if rule.apply_to:
                        should_apply = self.evaluator.evaluate(rule.apply_to, context)
                        if not should_apply:
                            continue
                    
                    # 执行规则表达式
                    field_value = self.evaluator.evaluate(rule.rule_expression, context)
                    
                    # 设置字段值
                    if field_value is not None:
                        success = self.evaluator._set_field_value(domain, rule.target_field, field_value)
                        if success:
                            log_entry = {
                                "type": "completion",
                                "status": "success",
                                "rule_name": rule.rule_name,
                                "target_field": rule.target_field,
                                "value": field_value if not isinstance(field_value, list) else f"[{len(field_value)} items]",
                                "message": f"CEL字段补全成功: {rule.rule_name} - {rule.target_field}"
                            }
                            self.execution_log.append(log_entry)
                            print(log_entry["message"])
                        else:
                            log_entry = {
                                "type": "completion",
                                "status": "failed",
                                "rule_name": rule.rule_name,
                                "target_field": rule.target_field,
                                "message": f"CEL字段补全失败: {rule.rule_name} - 无法设置字段 {rule.target_field}"
                            }
                            self.execution_log.append(log_entry)
                            print(log_entry["message"])
                            
                except Exception as e:
                    log_entry = {
                        "type": "completion",
                        "status": "error",
                        "rule_name": rule.rule_name,
                        "error": str(e),
                        "message": f"CEL字段补全错误: {rule.rule_name} - {str(e)}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
        
        return domain
    
    def _process_items_rule(self, rule: FieldCompletionRule, domain: InvoiceDomainObject):
        """处理items[]语法的规则"""
        logger.debug(f"_process_items_rule 开始: 规则={rule.rule_name}, 优先级={rule.priority}, 目标字段={rule.target_field}")
        
        # 从target_field中提取实际的字段名（去掉items[].前缀）
        item_field = rule.target_field.replace('items[].', '')
        logger.debug(f"提取的item字段名: {item_field}")
        
        # 检查domain是否有items字段
        if not hasattr(domain, 'items') or not domain.items:
            logger.debug(f"domain没有items字段或items为空")
            return
        
        logger.debug(f"domain.items 包含 {len(domain.items)} 个项目")
        
        # 为每个item创建上下文并处理
        for i, item in enumerate(domain.items):
            logger.debug(f"处理第 {i+1} 个item: {item.description if hasattr(item, 'description') else 'N/A'}")
            
            # 创建包含当前item的上下文
            context = {
                'invoice': domain,
                'item': item
            }
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    logger.debug(f"检查应用条件: {rule.apply_to}")
                    should_apply = self.evaluator.evaluate(rule.apply_to, context)
                    logger.debug(f"应用条件结果: {should_apply}")
                    
                    # 特殊调试：如果是补全商品税率规则，详细检查条件
                    if rule.rule_name == "补全商品税率":
                        has_tax_rate = self.evaluator.evaluate("has(item.tax_rate)", context)
                        not_has_tax_rate = self.evaluator.evaluate("!has(item.tax_rate)", context)
                        logger.debug(f"has(item.tax_rate): {has_tax_rate}")
                        logger.debug(f"!has(item.tax_rate): {not_has_tax_rate}")
                    
                    if not should_apply:
                        logger.debug(f"条件不满足，跳过此item")
                        continue
                
                # 执行规则表达式
                logger.debug(f"执行规则表达式: {rule.rule_expression}")
                field_value = self.evaluator.evaluate(rule.rule_expression, context)
                logger.debug(f"规则表达式结果: {field_value} (类型: {type(field_value)})")
                
                if field_value is not None:
                    # 直接设置字段值到item对象
                    try:
                        # 处理CEL类型转换
                        if hasattr(field_value, 'value'):
                            actual_value = field_value.value
                        else:
                            actual_value = field_value
                        
                        # 设置字段值
                        setattr(item, item_field, actual_value)
                        logger.debug(f"成功设置字段 {item_field} = {actual_value}")
                        
                        # 验证设置结果
                        new_value = getattr(item, item_field)
                        logger.debug(f"验证: 字段 {item_field} 新值: {new_value}")
                        
                        log_entry = {
                            "type": "completion",
                            "status": "success",
                            "rule_name": rule.rule_name,
                            "target_field": rule.target_field,
                            "item_index": i,
                            "value": actual_value,
                            "message": f"字段补全成功: {rule.rule_name} - 设置 items[{i}].{item_field} = {actual_value}"
                        }
                        self.execution_log.append(log_entry)
                        print(log_entry["message"])
                        
                    except Exception as e:
                        logger.debug(f"设置字段时发生错误: {str(e)}")
                        log_entry = {
                            "type": "completion",
                            "status": "failed",
                            "rule_name": rule.rule_name,
                            "target_field": rule.target_field,
                            "item_index": i,
                            "error": str(e),
                            "message": f"字段补全失败: {rule.rule_name} - 无法设置字段 items[{i}].{item_field}: {str(e)}"
                        }
                        self.execution_log.append(log_entry)
                        print(log_entry["message"])
                        
            except Exception as e:
                logger.debug(f"处理规则时发生错误: {str(e)}")
                log_entry = {
                    "type": "completion",
                    "status": "error",
                    "rule_name": rule.rule_name,
                    "target_field": rule.target_field,
                    "item_index": i,
                    "error": str(e),
                    "message": f"字段补全错误: {rule.rule_name} - {str(e)}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])


class DatabaseCELFieldCompletionEngine(CELFieldCompletionEngine):
    """支持数据库查询的CEL字段补全引擎"""
    
    def __init__(self, db_session: AsyncSession = None):
        super().__init__()
        self.evaluator = DatabaseCELExpressionEvaluator(db_session)
        self.db_session = db_session
    
    async def complete_async(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """异步执行字段补全"""
        self.execution_log = []  # 重置日志
        
        for rule in self.rules:
            if not rule.active:
                # 不记录未激活的规则日志
                continue
            
            try:
                # 检查是否是items[]规则
                if rule.target_field.startswith('items[].'):
                    await self._process_items_rule_async(rule, domain)
                else:
                    await self._process_single_field_rule_async(rule, domain)
                        
            except Exception as e:
                log_entry = {
                    "type": "completion",
                    "status": "error",
                    "rule_name": rule.rule_name,
                    "error": str(e),
                    "message": f"字段补全错误: {rule.rule_name} - {str(e)}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
        
        return domain
    
    async def _process_single_field_rule_async(self, rule: FieldCompletionRule, domain: InvoiceDomainObject):
        """异步处理单个字段规则"""
        context = {'invoice': domain}
        
        # 检查应用条件
        if rule.apply_to:
            should_apply = await self.evaluator.evaluate_async(rule.apply_to, context)
            if not should_apply:
                log_entry = {
                    "type": "completion",
                    "status": "skipped",
                    "rule_name": rule.rule_name,
                    "reason": "condition_not_met",
                    "condition": rule.apply_to,
                    "message": f"规则跳过: {rule.rule_name} - 条件不满足: {rule.apply_to}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
                return
        
        # 执行规则表达式
        field_value = await self.evaluator.evaluate_async(rule.rule_expression, context)
        
        # 设置字段值
        if field_value is not None:
            success = self.evaluator._set_field_value(domain, rule.target_field, field_value)
            if success:
                log_entry = {
                    "type": "completion",
                    "status": "success",
                    "rule_name": rule.rule_name,
                    "target_field": rule.target_field,
                    "value": field_value,
                    "message": f"字段补全成功: {rule.rule_name} - {rule.target_field} = {field_value}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
            else:
                log_entry = {
                    "type": "completion",
                    "status": "failed",
                    "rule_name": rule.rule_name,
                    "target_field": rule.target_field,
                    "message": f"字段补全失败: {rule.rule_name} - 无法设置字段 {rule.target_field}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
    
    async def _process_items_rule_async(self, rule: FieldCompletionRule, domain: InvoiceDomainObject):
        """异步处理items[]语法的规则"""
        # 从target_field中提取实际的字段名（去掉items[].前缀）
        item_field = rule.target_field.replace('items[].', '')
        
        # 检查domain是否有items字段
        if not hasattr(domain, 'items') or not domain.items:
            return
        
        # 为每个item创建上下文并处理
        for i, item in enumerate(domain.items):
            # 创建包含当前item的上下文
            context = {
                'invoice': domain,
                'item': item
            }
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = await self.evaluator.evaluate_async(rule.apply_to, context)
                    if not should_apply:
                        continue
                
                # 执行规则表达式
                field_value = await self.evaluator.evaluate_async(rule.rule_expression, context)
                
                if field_value is not None:
                    # 处理CEL类型转换
                    if hasattr(field_value, 'value'):
                        actual_value = field_value.value
                    else:
                        actual_value = field_value
                    
                    # 检查字段是否存在
                    if not hasattr(item, item_field):
                        log_entry = {
                            "type": "completion",
                            "status": "failed",
                            "rule_name": rule.rule_name,
                            "target_field": rule.target_field,
                            "item_index": i,
                            "error": f"字段 {item_field} 不存在",
                            "message": f"字段补全失败: {rule.rule_name} - item对象没有字段 {item_field}"
                        }
                        self.execution_log.append(log_entry)
                        print(log_entry["message"])
                        continue
                    
                    # 类型转换（如果需要）
                    if item_field == 'tax_rate' and isinstance(actual_value, (int, float)):
                        from decimal import Decimal
                        actual_value = Decimal(str(actual_value))
                        logger.debug(f"转换为Decimal: {actual_value}")
                    
                    # 设置字段值
                    setattr(item, item_field, actual_value)
                    logger.debug(f"成功设置字段 {item_field} = {actual_value}")
                    
                    # 验证设置结果
                    new_value = getattr(item, item_field)
                    logger.debug(f"验证: 字段 {item_field} 新值: {new_value}")
                    
                    log_entry = {
                        "type": "completion",
                        "status": "success",
                        "rule_name": rule.rule_name,
                        "target_field": rule.target_field,
                        "item_index": i,
                        "value": actual_value,
                        "message": f"字段补全成功: {rule.rule_name} - 设置 items[{i}].{item_field} = {actual_value}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
                        
            except Exception as e:
                logger.debug(f"处理规则时发生错误: {str(e)}")
                log_entry = {
                    "type": "completion",
                    "status": "error",
                    "rule_name": rule.rule_name,
                    "target_field": rule.target_field,
                    "item_index": i,
                    "error": str(e),
                    "message": f"字段补全错误: {rule.rule_name} - {str(e)}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])