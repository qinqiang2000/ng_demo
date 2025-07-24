"""CEL业务校验引擎模块

本模块包含基于CEL的业务校验功能，包括：
- 基础业务校验引擎
- 支持数据库查询的业务校验引擎
- 规则加载和执行
- 校验结果记录
"""

from typing import Any, Dict, List
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldValidationRule
from .cel_evaluator import CELExpressionEvaluator, DatabaseCELExpressionEvaluator
from ..utils.logger import get_logger

# 创建logger
logger = get_logger(__name__)


class CELBusinessValidationEngine:
    """基于CEL的业务校验引擎"""
    
    def __init__(self):
        self.evaluator = CELExpressionEvaluator()
        self.rules: List = []
        self.validation_errors: List[Dict[str, Any]] = []
        self.execution_log: List[Dict[str, Any]] = []  # 添加执行日志，使用结构化格式
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
            for rule_data in config.get('business_validation_rules', []):
                rule = FieldValidationRule(**rule_data)
                rules.append(rule)
            
            self.load_rules(rules)
            logger.debug(f"成功加载 {len(rules)} 条业务校验规则")
            
        except Exception as e:
            logger.debug(f"加载业务校验规则配置失败: {str(e)}")
    
    def load_rules(self, rules: List):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def validate(self, domain: InvoiceDomainObject) -> bool:
        """执行业务校验"""
        self.validation_errors = []  # 重置错误列表
        self.execution_log = []  # 重置执行日志
        context = {'invoice': domain}
        
        logger.debug(f"CEL业务校验开始，共有 {len(self.rules)} 条规则")
        
        for rule in self.rules:
            logger.debug(f"处理校验规则: {rule.rule_name}, active: {rule.active}")
            
            if not rule.active:
                logger.debug(f"规则 {rule.rule_name} 未激活，跳过")
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = self.evaluator.evaluate(rule.apply_to, context)
                    if not should_apply:
                        logger.debug(f"规则 {rule.rule_name} 条件不满足，跳过")
                        continue
                
                # 执行校验表达式
                validation_result = self.evaluator.evaluate(rule.rule_expression, context)
                
                # 如果校验失败，记录错误
                if not validation_result:
                    error_entry = {
                        "rule_name": rule.rule_name,
                        "error_message": rule.error_message,
                        "severity": getattr(rule, 'severity', 'error'),
                        "field": getattr(rule, 'field_path', None),
                        "validation_expression": rule.rule_expression
                    }
                    self.validation_errors.append(error_entry)
                    logger.debug(f"校验失败: {rule.rule_name} - {rule.error_message}")
                    self.execution_log.append({
                         "type": "validation",
                         "status": "failed",
                         "rule_name": rule.rule_name,
                         "error_message": rule.error_message,
                         "message": f"❌ {rule.rule_name}(failed)→ {rule.error_message}"
                     })
                else:
                    logger.debug(f"校验通过: {rule.rule_name}")
                    self.execution_log.append({
                         "type": "validation",
                         "status": "success",
                         "rule_name": rule.rule_name,
                         "message": f"✅ {rule.rule_name}(success)→ 校验通过"
                     })
                    
            except Exception as e:
                error_entry = {
                    "rule_name": rule.rule_name,
                    "error_message": f"校验规则执行错误: {str(e)}",
                    "severity": "error",
                    "field": getattr(rule, 'field_path', None),
                    "validation_expression": rule.rule_expression,
                    "exception": str(e)
                }
                self.validation_errors.append(error_entry)
                logger.debug(f"校验规则执行错误: {rule.rule_name} - {str(e)}")
                self.execution_log.append({
                     "type": "validation",
                     "status": "error",
                     "rule_name": rule.rule_name,
                     "exception": str(e),
                     "error_message": f"校验规则执行错误: {str(e)}",
                     "message": f"❌ {rule.rule_name}(error)→ 执行错误: {str(e)}"
                 })
        
        # 返回是否通过所有校验
        is_valid = len(self.validation_errors) == 0
        logger.debug(f"业务校验完成，结果: {'通过' if is_valid else '失败'}，错误数量: {len(self.validation_errors)}")
        
        # 移除最终状态日志，保持与字段补全一致
        
        # 返回校验结果和错误列表
        error_messages = [error["error_message"] for error in self.validation_errors]
        return is_valid, error_messages
    
    def get_validation_errors(self) -> List[Dict[str, Any]]:
        """获取校验错误列表"""
        return self.validation_errors
    
    def get_error_summary(self) -> str:
        """获取错误摘要"""
        if not self.validation_errors:
            return "所有校验通过"
        
        error_count = len(self.validation_errors)
        error_messages = [error["error_message"] for error in self.validation_errors]
        
        return f"发现 {error_count} 个校验错误:\n" + "\n".join(f"- {msg}" for msg in error_messages)


class DatabaseCELBusinessValidationEngine(CELBusinessValidationEngine):
    """支持数据库查询的CEL业务校验引擎"""
    
    def __init__(self, db_session: AsyncSession = None):
        super().__init__()
        self.evaluator = DatabaseCELExpressionEvaluator(db_session)
        self.db_session = db_session
        self.execution_log: List[Dict[str, Any]] = []  # 添加执行日志，使用结构化格式确保有执行日志属性
    
    async def validate_async(self, domain: InvoiceDomainObject) -> bool:
        """异步执行业务校验"""
        self.validation_errors = []  # 重置错误列表
        self.execution_log = []  # 重置执行日志
        context = {'invoice': domain}
        
        logger.debug(f"异步CEL业务校验开始，共有 {len(self.rules)} 条规则")
        
        for rule in self.rules:
            logger.debug(f"处理校验规则: {rule.rule_name}, active: {rule.active}")
            
            if not rule.active:
                logger.debug(f"规则 {rule.rule_name} 未激活，跳过")
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = await self.evaluator.evaluate_async(rule.apply_to, context)
                    if not should_apply:
                        logger.debug(f"规则 {rule.rule_name} 条件不满足，跳过")
                        continue
                
                # 执行校验表达式
                validation_result = await self.evaluator.evaluate_async(rule.rule_expression, context)
                
                # 如果校验失败，记录错误
                if not validation_result:
                    error_entry = {
                        "rule_name": rule.rule_name,
                        "error_message": rule.error_message,
                        "severity": getattr(rule, 'severity', 'error'),
                        "field": getattr(rule, 'field_path', None),
                        "validation_expression": rule.rule_expression
                    }
                    self.validation_errors.append(error_entry)
                    logger.debug(f"校验失败: {rule.rule_name} - {rule.error_message}")
                    self.execution_log.append({
                         "type": "validation",
                         "status": "failed",
                         "rule_name": rule.rule_name,
                         "error_message": rule.error_message,
                         "message": f"❌ {rule.rule_name}(failed)→ {rule.error_message}"
                     })
                else:
                    logger.debug(f"校验通过: {rule.rule_name}")
                    self.execution_log.append({
                         "type": "validation",
                         "status": "success",
                         "rule_name": rule.rule_name,
                         "message": f"✅ {rule.rule_name}(success)→ 校验通过"
                     })
                    
            except Exception as e:
                error_entry = {
                    "rule_name": rule.rule_name,
                    "error_message": f"校验规则执行错误: {str(e)}",
                    "severity": "error",
                    "field": getattr(rule, 'field_path', None),
                    "validation_expression": rule.rule_expression,
                    "exception": str(e)
                }
                self.validation_errors.append(error_entry)
                logger.debug(f"校验规则执行错误: {rule.rule_name} - {str(e)}")
                self.execution_log.append({
                     "type": "validation",
                     "status": "error",
                     "rule_name": rule.rule_name,
                     "exception": str(e),
                     "error_message": f"校验规则执行错误: {str(e)}",
                     "message": f"❌ {rule.rule_name}(error)→ 执行错误: {str(e)}"
                 })
        
        # 返回是否通过所有校验
        is_valid = len(self.validation_errors) == 0
        logger.debug(f"异步业务校验完成，结果: {'通过' if is_valid else '失败'}，错误数量: {len(self.validation_errors)}")
        
        # 移除最终状态日志，保持与字段补全一致
        
        # 返回校验结果和错误列表
        error_messages = [error["error_message"] for error in self.validation_errors]
        return is_valid, error_messages