"""Google CEL (Common Expression Language) 引擎实现"""
import celpy
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..database.crud import DatabaseQueryHelper


class CELExpressionEvaluator:
    """基于Google CEL的表达式求值器"""
    
    def __init__(self):
        self.env = celpy.Environment()
        self._setup_custom_functions()
    
    def _setup_custom_functions(self):
        """设置自定义函数"""
        # CEL环境支持标准函数，这里可以扩展自定义函数
        # 注意：cel-python支持标准CEL内置函数如has()、contains()、matches()等
        
        # 为发票业务添加一些自定义函数示例
        # CEL-Python中添加自定义函数需要通过env配置
        pass
    
    def evaluate(self, expression: str, context: Dict[str, Any]) -> Any:
        """使用CEL计算表达式"""
        try:
            # 编译CEL表达式
            ast = self.env.compile(expression)
            program = self.env.program(ast)
            
            # 准备CEL上下文
            cel_context = self._prepare_cel_context(context)
            
            # 执行表达式
            result = program.evaluate(cel_context)
            
            # 转换结果类型
            return self._convert_result(result)
            
        except Exception as e:
            print(f"CEL表达式执行错误: {expression} - {str(e)}")
            raise e
    
    def _prepare_cel_context(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """准备CEL执行上下文"""
        cel_context = {}
        
        for key, value in context.items():
            if isinstance(value, InvoiceDomainObject):
                # 将Domain Object转换为CEL可识别的格式
                cel_context[key] = self._domain_object_to_cel(value)
            else:
                cel_context[key] = celpy.json_to_cel(value)
        
        return cel_context
    
    def _domain_object_to_cel(self, domain_obj: InvoiceDomainObject) -> Any:
        """将Domain Object转换为CEL对象"""
        # 将Pydantic模型转换为字典，然后转为CEL对象
        domain_dict = self._pydantic_to_dict(domain_obj)
        return celpy.json_to_cel(domain_dict)
    
    def _pydantic_to_dict(self, obj: Any) -> Dict[str, Any]:
        """将Pydantic对象转换为字典"""
        if hasattr(obj, 'model_dump'):
            # Pydantic v2
            data = obj.model_dump()
        elif hasattr(obj, 'dict'):
            # Pydantic v1
            data = obj.dict()
        else:
            # 普通对象
            data = obj if isinstance(obj, dict) else vars(obj)
        
        # 转换日期对象为字符串，CEL-Python不支持date对象
        return self._convert_dates_to_strings(data)
    
    def _convert_dates_to_strings(self, data: Any) -> Any:
        """递归转换特殊对象为CEL兼容类型"""
        if isinstance(data, dict):
            return {k: self._convert_dates_to_strings(v) for k, v in data.items()}
        elif isinstance(data, list):
            return [self._convert_dates_to_strings(item) for item in data]
        elif hasattr(data, 'isoformat'):
            # datetime.date, datetime.datetime 对象
            return data.isoformat()
        elif isinstance(data, Decimal):
            # Decimal转为float，CEL支持float
            return float(data)
        else:
            return data
    
    def _convert_result(self, result: Any) -> Any:
        """转换CEL结果为Python类型"""
        if hasattr(result, 'value'):
            # CEL值对象，提取实际值
            return result.value
        elif isinstance(result, (int, float, str, bool)):
            return result
        elif isinstance(result, dict):
            return result
        else:
            return result
    
    def _set_field_value(self, obj: Any, field_path: str, value: Any):
        """设置字段值"""
        parts = field_path.split('.')
        current = obj
        
        # 导航到父对象
        for part in parts[:-1]:
            if hasattr(current, part):
                current = getattr(current, part)
            else:
                return False
        
        # 设置最后一个字段
        if hasattr(current, parts[-1]):
            # 类型转换
            if isinstance(value, (int, float)) and hasattr(current, parts[-1]):
                # 检查目标字段类型
                current_value = getattr(current, parts[-1])
                if isinstance(current_value, Decimal) or parts[-1] in ['total_amount', 'tax_amount', 'net_amount']:
                    value = Decimal(str(value))
            
            setattr(current, parts[-1], value)
            return True
        elif isinstance(current, dict):
            current[parts[-1]] = value
            return True
        
        return False


class CELFieldCompletionEngine:
    """基于CEL的字段补全引擎"""
    
    def __init__(self):
        self.evaluator = CELExpressionEvaluator()
        self.rules: List = []
        self.execution_log: List[Dict[str, Any]] = []
    
    def load_rules(self, rules: List):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def complete(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """执行字段补全"""
        self.execution_log = []  # 重置日志
        context = {'invoice': domain}
        
        for rule in self.rules:
            if not rule.active:
                continue
            
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
                            "value": field_value,
                            "message": f"CEL字段补全成功: {rule.rule_name} - {rule.target_field} = {field_value}"
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


class CELBusinessValidationEngine:
    """基于CEL的业务校验引擎"""
    
    def __init__(self):
        self.evaluator = CELExpressionEvaluator()
        self.rules: List = []
        self.execution_log: List[Dict[str, Any]] = []
    
    def load_rules(self, rules: List):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def validate(self, domain: InvoiceDomainObject) -> Tuple[bool, List[str]]:
        """执行业务校验"""
        self.execution_log = []  # 重置日志
        context = {'invoice': domain}
        errors = []
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = self.evaluator.evaluate(rule.apply_to, context)
                    if not should_apply:
                        continue
                
                # 执行校验规则
                is_valid = self.evaluator.evaluate(rule.rule_expression, context)
                
                if not is_valid:
                    errors.append(f"{rule.rule_name}: {rule.error_message}")
                    log_entry = {
                        "type": "validation",
                        "status": "failed",
                        "rule_name": rule.rule_name,
                        "error_message": rule.error_message,
                        "message": f"CEL校验失败: {rule.rule_name} - {rule.error_message}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
                else:
                    log_entry = {
                        "type": "validation",
                        "status": "passed",
                        "rule_name": rule.rule_name,
                        "message": f"CEL校验通过: {rule.rule_name}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
                    
            except Exception as e:
                error_msg = f"{rule.rule_name}: CEL规则执行错误 - {str(e)}"
                errors.append(error_msg)
                log_entry = {
                    "type": "validation",
                    "status": "error",
                    "rule_name": rule.rule_name,
                    "error": str(e),
                    "message": f"CEL校验错误: {rule.rule_name} - {str(e)}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
        
        return len(errors) == 0, errors


class DatabaseCELExpressionEvaluator(CELExpressionEvaluator):
    """支持数据库查询的CEL表达式求值器"""
    
    def __init__(self, db_session: AsyncSession = None):
        super().__init__()
        self.db_session = db_session
    
    async def evaluate_async(self, expression: str, context: Dict[str, Any]) -> Any:
        """异步计算表达式，支持数据库查询"""
        try:
            # 先检查是否包含数据库查询函数
            if any(func in expression for func in ['db_query_tax_number_by_name', 'db_query_tax_rate_by_category_and_amount', 'db_query_company_category_by_name']):
                return await self._evaluate_with_db_queries(expression, context)
            else:
                # 使用标准CEL评估
                return self.evaluate(expression, context)
        except Exception as e:
            print(f"CEL表达式执行错误: {expression} - {str(e)}")
            return None
    
    async def _evaluate_with_db_queries(self, expression: str, context: Dict[str, Any]) -> Any:
        """处理包含数据库查询的表达式"""
        if not self.db_session:
            print(f"数据库会话为空，无法执行数据库查询: {expression}")
            return None
        
        try:
            # 处理数据库查询函数
            processed_expression = expression
            
            # 处理 db_query_tax_number_by_name
            if 'db_query_tax_number_by_name(' in expression:
                processed_expression = await self._replace_db_query_tax_number(processed_expression, context)
            
            # 处理 db_query_tax_rate_by_category_and_amount
            if 'db_query_tax_rate_by_category_and_amount(' in processed_expression:
                processed_expression = await self._replace_db_query_tax_rate(processed_expression, context)
            
            # 处理 db_query_company_category_by_name
            if 'db_query_company_category_by_name(' in processed_expression:
                processed_expression = await self._replace_db_query_category(processed_expression, context)
            
            # 使用CEL计算处理后的表达式
            if processed_expression != expression:
                return self.evaluate(processed_expression, context)
            else:
                return self.evaluate(expression, context)
                
        except Exception as e:
            print(f"数据库查询表达式处理错误: {expression} - {str(e)}")
            return None
    
    async def _replace_db_query_tax_number(self, expression: str, context: Dict[str, Any]) -> str:
        """替换税号查询函数"""
        import re
        pattern = r'db_query_tax_number_by_name\(([^)]+)\)'
        
        def replace_func(match):
            param = match.group(1).strip()
            # 解析参数
            if param.startswith('invoice.'):
                field_value = self._get_field_value_from_context(param, context)
                if field_value:
                    # 执行数据库查询（同步版本，需要改为异步）
                    # 这里需要特殊处理，因为CEL是同步的
                    return f'"{field_value}_tax_number"'  # 临时替换
            return '""'
        
        # 实际实现中，我们需要预先查询数据库
        for match in re.finditer(pattern, expression):
            param = match.group(1).strip()
            if param.startswith('invoice.'):
                field_value = self._get_field_value_from_context(param, context)
                if field_value:
                    tax_number = await DatabaseQueryHelper.get_company_tax_number_by_name(self.db_session, field_value)
                    if tax_number:
                        expression = expression.replace(match.group(0), f'"{tax_number}"')
                    else:
                        expression = expression.replace(match.group(0), '""')
        
        return expression
    
    async def _replace_db_query_tax_rate(self, expression: str, context: Dict[str, Any]) -> str:
        """替换税率查询函数"""
        import re
        pattern = r'db_query_tax_rate_by_category_and_amount\(([^,]+),\s*([^)]+)\)'
        
        for match in re.finditer(pattern, expression):
            category_param = match.group(1).strip()
            amount_param = match.group(2).strip()
            
            category = self._get_field_value_from_context(category_param, context)
            amount = self._get_field_value_from_context(amount_param, context)
            
            if category and amount:
                try:
                    tax_rate = await DatabaseQueryHelper.get_tax_rate_by_category_and_amount(
                        self.db_session, category, float(amount)
                    )
                    if tax_rate:
                        expression = expression.replace(match.group(0), str(tax_rate))
                    else:
                        expression = expression.replace(match.group(0), '0.06')  # 默认税率
                except:
                    expression = expression.replace(match.group(0), '0.06')
            else:
                expression = expression.replace(match.group(0), '0.06')
        
        return expression
    
    async def _replace_db_query_category(self, expression: str, context: Dict[str, Any]) -> str:
        """替换企业分类查询函数"""
        import re
        pattern = r'db_query_company_category_by_name\(([^)]+)\)'
        
        for match in re.finditer(pattern, expression):
            param = match.group(1).strip()
            field_value = self._get_field_value_from_context(param, context)
            
            if field_value:
                category = await DatabaseQueryHelper.get_company_category_by_name(self.db_session, field_value)
                if category:
                    expression = expression.replace(match.group(0), f'"{category}"')
                else:
                    expression = expression.replace(match.group(0), '"GENERAL"')
            else:
                expression = expression.replace(match.group(0), '"GENERAL"')
        
        return expression
    
    def _get_field_value_from_context(self, field_path: str, context: Dict[str, Any]) -> Any:
        """从上下文中获取字段值"""
        if not field_path.startswith('invoice.'):
            return None
        
        parts = field_path[8:].split('.')  # 去掉 'invoice.' 前缀
        current = context.get('invoice')
        
        if not current:
            return None
        
        for part in parts:
            if hasattr(current, part):
                current = getattr(current, part)
            else:
                return None
        
        return current


class DatabaseCELFieldCompletionEngine(CELFieldCompletionEngine):
    """支持数据库查询的CEL字段补全引擎"""
    
    def __init__(self, db_session: AsyncSession = None):
        super().__init__()
        self.evaluator = DatabaseCELExpressionEvaluator(db_session)
        self.db_session = db_session
    
    async def complete_async(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """异步执行字段补全"""
        self.execution_log = []  # 重置日志
        context = {'invoice': domain}
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = await self.evaluator.evaluate_async(rule.apply_to, context)
                    if not should_apply:
                        continue
                
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


class DatabaseCELBusinessValidationEngine(CELBusinessValidationEngine):
    """支持数据库查询的CEL业务校验引擎"""
    
    def __init__(self, db_session: AsyncSession = None):
        super().__init__()
        self.evaluator = DatabaseCELExpressionEvaluator(db_session)
        self.db_session = db_session
    
    async def validate_async(self, domain: InvoiceDomainObject) -> Tuple[bool, List[str]]:
        """异步执行业务校验"""
        self.execution_log = []  # 重置日志
        context = {'invoice': domain}
        errors = []
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = await self.evaluator.evaluate_async(rule.apply_to, context)
                    if not should_apply:
                        continue
                
                # 执行校验规则
                is_valid = await self.evaluator.evaluate_async(rule.rule_expression, context)
                
                if not is_valid:
                    errors.append(f"{rule.rule_name}: {rule.error_message}")
                    log_entry = {
                        "type": "validation",
                        "status": "failed",
                        "rule_name": rule.rule_name,
                        "error_message": rule.error_message,
                        "message": f"校验失败: {rule.rule_name} - {rule.error_message}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
                else:
                    log_entry = {
                        "type": "validation",
                        "status": "passed",
                        "rule_name": rule.rule_name,
                        "message": f"校验通过: {rule.rule_name}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
                    
            except Exception as e:
                error_msg = f"{rule.rule_name}: 规则执行错误 - {str(e)}"
                errors.append(error_msg)
                log_entry = {
                    "type": "validation",
                    "status": "error",
                    "rule_name": rule.rule_name,
                    "error": str(e),
                    "message": f"校验错误: {rule.rule_name} - {str(e)}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
        
        return len(errors) == 0, errors