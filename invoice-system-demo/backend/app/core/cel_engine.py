"""CEL引擎主入口模块

本模块作为CEL引擎的主入口，提供统一的接口访问：
1. CEL表达式求值功能
2. 字段补全功能
3. 业务校验功能

模块已重构为以下子模块：
- cel_evaluator: 核心表达式求值功能
- cel_field_completion: 字段补全功能
- cel_validation: 业务校验功能
"""

from typing import Any, Dict, List
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule, FieldValidationRule
from ..utils.logger import get_logger

# 导入子模块
from .cel_evaluator import CELExpressionEvaluator, DatabaseCELExpressionEvaluator
from .cel_field_completion import CELFieldCompletionEngine, DatabaseCELFieldCompletionEngine
from .cel_validation import CELBusinessValidationEngine, DatabaseCELBusinessValidationEngine

# 创建logger
logger = get_logger(__name__)


# 为了保持向后兼容性，保留原有的类名作为别名
# 这样现有代码无需修改即可使用新的模块化结构

# 表达式求值器别名
CELExpressionEvaluator = CELExpressionEvaluator
DatabaseCELExpressionEvaluator = DatabaseCELExpressionEvaluator

# 字段补全引擎别名
CELFieldCompletionEngine = CELFieldCompletionEngine
DatabaseCELFieldCompletionEngine = DatabaseCELFieldCompletionEngine

# 业务校验引擎别名
CELBusinessValidationEngine = CELBusinessValidationEngine
DatabaseCELBusinessValidationEngine = DatabaseCELBusinessValidationEngine


# 提供便捷的工厂函数
def create_cel_evaluator(db_session: AsyncSession = None) -> CELExpressionEvaluator:
    """创建CEL表达式求值器
    
    Args:
        db_session: 数据库会话，如果提供则返回支持数据库查询的求值器
        
    Returns:
        CEL表达式求值器实例
    """
    if db_session:
        return DatabaseCELExpressionEvaluator(db_session)
    else:
        return CELExpressionEvaluator()


def create_field_completion_engine(db_session: AsyncSession = None) -> CELFieldCompletionEngine:
    """创建字段补全引擎
    
    Args:
        db_session: 数据库会话，如果提供则返回支持数据库查询的字段补全引擎
        
    Returns:
        字段补全引擎实例
    """
    if db_session:
        return DatabaseCELFieldCompletionEngine(db_session)
    else:
        return CELFieldCompletionEngine()


def create_validation_engine(db_session: AsyncSession = None) -> CELBusinessValidationEngine:
    """创建业务校验引擎
    
    Args:
        db_session: 数据库会话，如果提供则返回支持数据库查询的业务校验引擎
        
    Returns:
        业务校验引擎实例
    """
    if db_session:
        return DatabaseCELBusinessValidationEngine(db_session)
    else:
        return CELBusinessValidationEngine()


class CELEngineManager:
    """CEL引擎管理器
    
    提供统一的接口来管理和使用所有CEL引擎功能
    """
    
    def __init__(self, db_session: AsyncSession = None):
        """初始化CEL引擎管理器
        
        Args:
            db_session: 数据库会话，如果提供则所有引擎都支持数据库查询
        """
        self.db_session = db_session
        self.evaluator = create_cel_evaluator(db_session)
        self.field_completion_engine = create_field_completion_engine(db_session)
        self.validation_engine = create_validation_engine(db_session)
        
        logger.info(f"CEL引擎管理器初始化完成，数据库支持: {'是' if db_session else '否'}")
    
    def evaluate_expression(self, expression: str, context: Dict[str, Any]) -> Any:
        """求值CEL表达式
        
        Args:
            expression: CEL表达式
            context: 上下文变量
            
        Returns:
            表达式求值结果
        """
        if hasattr(self.evaluator, 'evaluate_async'):
            # 如果是异步求值器，需要在异步环境中调用
            raise RuntimeError("请使用 evaluate_expression_async 方法进行异步求值")
        
        return self.evaluator.evaluate(expression, context)
    
    async def evaluate_expression_async(self, expression: str, context: Dict[str, Any]) -> Any:
        """异步求值CEL表达式
        
        Args:
            expression: CEL表达式
            context: 上下文变量
            
        Returns:
            表达式求值结果
        """
        if hasattr(self.evaluator, 'evaluate_async'):
            return await self.evaluator.evaluate_async(expression, context)
        else:
            return self.evaluator.evaluate(expression, context)
    
    def complete_fields(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """执行字段补全
        
        Args:
            domain: 发票领域对象
            
        Returns:
            补全后的发票领域对象
        """
        if hasattr(self.field_completion_engine, 'complete_async'):
            # 如果是异步引擎，需要在异步环境中调用
            raise RuntimeError("请使用 complete_fields_async 方法进行异步字段补全")
        
        return self.field_completion_engine.complete(domain)
    
    async def complete_fields_async(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """异步执行字段补全
        
        Args:
            domain: 发票领域对象
            
        Returns:
            补全后的发票领域对象
        """
        if hasattr(self.field_completion_engine, 'complete_async'):
            return await self.field_completion_engine.complete_async(domain)
        else:
            return self.field_completion_engine.complete(domain)
    
    def validate_business_rules(self, domain: InvoiceDomainObject) -> bool:
        """执行业务校验
        
        Args:
            domain: 发票领域对象
            
        Returns:
            校验是否通过
        """
        if hasattr(self.validation_engine, 'validate_async'):
            # 如果是异步引擎，需要在异步环境中调用
            raise RuntimeError("请使用 validate_business_rules_async 方法进行异步业务校验")
        
        return self.validation_engine.validate(domain)
    
    async def validate_business_rules_async(self, domain: InvoiceDomainObject) -> bool:
        """异步执行业务校验
        
        Args:
            domain: 发票领域对象
            
        Returns:
            校验是否通过
        """
        if hasattr(self.validation_engine, 'validate_async'):
            return await self.validation_engine.validate_async(domain)
        else:
            return self.validation_engine.validate(domain)
    
    def get_field_completion_log(self) -> List[Dict[str, Any]]:
        """获取字段补全执行日志
        
        Returns:
            执行日志列表
        """
        return getattr(self.field_completion_engine, 'execution_log', [])
    
    def get_validation_errors(self) -> List[Dict[str, Any]]:
        """获取业务校验错误列表
        
        Returns:
            校验错误列表
        """
        return self.validation_engine.get_validation_errors()
    
    def get_validation_error_summary(self) -> str:
        """获取业务校验错误摘要
        
        Returns:
            错误摘要字符串
        """
        return self.validation_engine.get_error_summary()


# 提供全局便捷函数
def process_invoice_with_cel(domain: InvoiceDomainObject, 
                           db_session: AsyncSession = None,
                           enable_field_completion: bool = True,
                           enable_validation: bool = True) -> tuple[InvoiceDomainObject, bool, List[Dict[str, Any]]]:
    """使用CEL引擎处理发票
    
    Args:
        domain: 发票领域对象
        db_session: 数据库会话
        enable_field_completion: 是否启用字段补全
        enable_validation: 是否启用业务校验
        
    Returns:
        元组：(处理后的发票对象, 校验是否通过, 处理日志)
    """
    manager = CELEngineManager(db_session)
    
    # 字段补全
    if enable_field_completion:
        domain = manager.complete_fields(domain)
    
    # 业务校验
    validation_passed = True
    if enable_validation:
        validation_passed = manager.validate_business_rules(domain)
    
    # 收集处理日志
    processing_log = []
    processing_log.extend(manager.get_field_completion_log())
    
    if not validation_passed:
        processing_log.extend([
            {
                "type": "validation",
                "status": "failed",
                "errors": manager.get_validation_errors(),
                "summary": manager.get_validation_error_summary()
            }
        ])
    
    return domain, validation_passed, processing_log


async def process_invoice_with_cel_async(domain: InvoiceDomainObject, 
                                       db_session: AsyncSession = None,
                                       enable_field_completion: bool = True,
                                       enable_validation: bool = True) -> tuple[InvoiceDomainObject, bool, List[Dict[str, Any]]]:
    """异步使用CEL引擎处理发票
    
    Args:
        domain: 发票领域对象
        db_session: 数据库会话
        enable_field_completion: 是否启用字段补全
        enable_validation: 是否启用业务校验
        
    Returns:
        元组：(处理后的发票对象, 校验是否通过, 处理日志)
    """
    manager = CELEngineManager(db_session)
    
    # 字段补全
    if enable_field_completion:
        domain = await manager.complete_fields_async(domain)
    
    # 业务校验
    validation_passed = True
    if enable_validation:
        validation_passed = await manager.validate_business_rules_async(domain)
    
    # 收集处理日志
    processing_log = []
    processing_log.extend(manager.get_field_completion_log())
    
    if not validation_passed:
        processing_log.extend([
            {
                "type": "validation",
                "status": "failed",
                "errors": manager.get_validation_errors(),
                "summary": manager.get_validation_error_summary()
            }
        ])
    
    return domain, validation_passed, processing_log