"""CEL表达式求值器模块

本模块包含CEL表达式求值的核心功能，包括：
- 基础CEL表达式求值器
- 支持数据库查询的CEL表达式求值器
- 自定义函数处理
- 产品API函数集成
- 数据库查询函数处理
"""

import celpy
from decimal import Decimal
from typing import Any, Dict, List, Optional
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..database.crud import DatabaseQueryHelper
from ..utils.logger import get_logger

# 创建logger
logger = get_logger(__name__)


class CELExpressionEvaluator:
    """基于Google CEL的表达式求值器"""
    
    def __init__(self):
        self.env = celpy.Environment()
        self._setup_custom_functions()
    
    def _setup_custom_functions(self):
        """设置自定义函数"""
        # CEL环境支持标准函数，这里可以扩展自定义函数
        # 注意：cel-python支持标准CEL内置函数如has()、contains()、matches()等
        
        # 注册产品API函数到CEL环境
        from ..services.product_api_service import product_api
        
        # 创建自定义函数映射
        self.custom_functions = {
            'get_standard_name': lambda desc: product_api.get_standard_name(desc),
            'get_tax_rate': lambda desc: product_api.get_tax_rate(desc),
            'get_tax_category': lambda desc: product_api.get_tax_category(desc)
        }
        
        # cel-python 不支持动态函数注册，我们使用预处理方式
        logger.debug(f"CEL自定义函数已准备: {list(self.custom_functions.keys())}")
    
    def evaluate(self, expression: str, context: Dict[str, Any]) -> Any:
        """使用CEL计算表达式"""
        try:
            # 先处理产品API函数调用
            processed_expression = self._process_product_api_functions(expression, context)
            
            # 编译CEL表达式
            ast = self.env.compile(processed_expression)
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
        
        # 添加自定义函数到上下文
        cel_context.update(self.custom_functions)
        
        for key, value in context.items():
            if isinstance(value, InvoiceDomainObject):
                # 将Domain Object转换为CEL可识别的格式
                cel_context[key] = self._domain_object_to_cel(value)
            elif hasattr(value, 'model_dump') or hasattr(value, 'dict'):
                # 处理其他Pydantic对象（如InvoiceItem）
                cel_context[key] = self._domain_object_to_cel(value)
            elif isinstance(value, dict):
                cel_context[key] = celpy.json_to_cel(value)
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
    
    def _process_product_api_functions(self, expression: str, context: Dict[str, Any]) -> str:
        """处理产品API函数调用"""
        import re
        from ..services.product_api_service import product_api
        
        # 处理 get_standard_name() 函数
        pattern = r'get_standard_name\(([^)]+)\)'
        def replace_get_standard_name(match):
            param = match.group(1).strip()
            # 评估参数表达式
            param_value = self._evaluate_parameter(param, context)
            if param_value is not None:
                result = product_api.get_standard_name(str(param_value))
                return f'"{result}"'  # 返回字符串字面量
            return '""'
        
        expression = re.sub(pattern, replace_get_standard_name, expression)
        
        # 处理 get_tax_rate() 函数
        pattern = r'get_tax_rate\(([^)]+)\)'
        def replace_get_tax_rate(match):
            param = match.group(1).strip()
            # 评估参数表达式
            param_value = self._evaluate_parameter(param, context)
            if param_value is not None:
                result = product_api.get_tax_rate(str(param_value))
                return str(result)  # 返回数值字面量
            return '0.0'
        
        expression = re.sub(pattern, replace_get_tax_rate, expression)
        
        # 处理 get_tax_category() 函数
        pattern = r'get_tax_category\(([^)]+)\)'
        def replace_get_tax_category(match):
            param = match.group(1).strip()
            # 评估参数表达式
            param_value = self._evaluate_parameter(param, context)
            if param_value is not None:
                result = product_api.get_tax_category(str(param_value))
                return f'"{result}"'  # 返回字符串字面量
            return '""'
        
        expression = re.sub(pattern, replace_get_tax_category, expression)
        
        return expression
    
    def _evaluate_parameter(self, param_expr: str, context: Dict[str, Any]) -> Any:
        """评估参数表达式"""
        param_expr = param_expr.strip()
        
        # 如果是字符串字面量，直接返回
        if param_expr.startswith('"') and param_expr.endswith('"'):
            return param_expr[1:-1]
        if param_expr.startswith("'") and param_expr.endswith("'"):
            return param_expr[1:-1]
        
        # 如果是字段访问表达式，从context中获取值
        try:
            # 简单的字段访问解析
            parts = param_expr.split('.')
            current = context
            for part in parts:
                if isinstance(current, dict):
                    current = current.get(part)
                elif hasattr(current, part):
                    current = getattr(current, part)
                else:
                    return None
            return current
        except:
            return None
    
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
        logger.debug(f"_set_field_value 开始: obj={type(obj).__name__}, field_path={field_path}, value={value} (type: {type(value)})")
        
        parts = field_path.split('.')
        current = obj
        
        logger.debug(f"字段路径分解: {parts}")
        
        # 导航到父对象
        for i, part in enumerate(parts[:-1]):
            logger.debug(f"导航到字段 {i}: {part}")
            if hasattr(current, part):
                current = getattr(current, part)
                logger.debug(f"成功获取字段 {part}, 当前对象类型: {type(current).__name__}")
            else:
                logger.debug(f"字段 {part} 不存在于对象 {type(current).__name__}")
                return False
        
        # 设置最后一个字段
        final_field = parts[-1]
        logger.debug(f"准备设置最终字段: {final_field}")
        logger.debug(f"当前对象类型: {type(current).__name__}")
        logger.debug(f"当前对象是否有该字段: {hasattr(current, final_field)}")
        
        if hasattr(current, final_field):
            # 获取当前字段值用于调试
            current_value = getattr(current, final_field)
            logger.debug(f"字段 {final_field} 当前值: {current_value} (type: {type(current_value)})")
            
            # 类型转换
            original_value = value
            if isinstance(value, (int, float)) and hasattr(current, final_field):
                # 检查目标字段类型
                if isinstance(current_value, Decimal) or final_field in ['total_amount', 'tax_amount', 'net_amount', 'tax_rate', 'quantity', 'unit_price', 'amount']:
                    value = Decimal(str(value))
                    logger.debug(f"类型转换: {original_value} ({type(original_value)}) -> {value} ({type(value)})")
            
            # 处理列表类型的特殊转换
            if isinstance(value, list) and final_field == 'items':
                # 将CEL对象列表转换为Pydantic对象列表
                from ..models.domain import InvoiceItem
                converted_items = []
                for item_data in value:
                    if isinstance(item_data, dict):
                        # 将字典转换为InvoiceItem对象
                        converted_items.append(InvoiceItem(**item_data))
                    else:
                        converted_items.append(item_data)
                    logger.debug(f"converted_items: {converted_items}")
                value = converted_items
            
            try:
                setattr(current, final_field, value)
                # 验证设置是否成功
                new_value = getattr(current, final_field)
                logger.debug(f"字段设置成功: {final_field} = {new_value} (type: {type(new_value)})")
                return True
            except Exception as e:
                logger.debug(f"字段设置失败: {final_field}, 错误: {str(e)}")
                return False
                
        elif isinstance(current, dict):
            logger.debug(f"当前对象是字典，直接设置键值")
            current[final_field] = value
            logger.debug(f"字典设置成功: {final_field} = {value}")
            return True
        else:
            logger.debug(f"无法设置字段: 对象 {type(current).__name__} 没有字段 {final_field}")
        
        return False


class DatabaseCELExpressionEvaluator(CELExpressionEvaluator):
    """支持数据库查询的CEL表达式求值器"""
    
    def __init__(self, db_session: AsyncSession = None):
        super().__init__()
        self.db_session = db_session
    
    async def evaluate_async(self, expression: str, context: Dict[str, Any]) -> Any:
        """异步计算表达式，支持数据库查询"""
        try:
            # 检查是否包含数据库查询函数（支持通用db_query和具体函数名）
            if 'db_query(' in expression or any(func in expression for func in ['db_query_tax_number_by_name(', 'db_query_tax_rate_by_category_and_amount(', 'db_query_company_category_by_name(']):
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
            
            # 处理通用 db_query 函数
            if 'db_query(' in expression:
                processed_expression = await self._replace_generic_db_query(processed_expression, context)
            
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
    
    async def _replace_generic_db_query(self, expression: str, context: Dict[str, Any]) -> str:
        """替换通用db_query函数调用"""
        import re
        pattern = r"db_query\('([^']+)'(?:,\s*([^)]+))?\)"
        
        for match in re.finditer(pattern, expression):
            query_name = match.group(1)
            params_str = match.group(2) if match.group(2) else None
            
            # 解析参数
            params = []
            if params_str:
                # 简单的参数解析，支持字符串和字段引用
                param_parts = [p.strip() for p in params_str.split(',')]
                for param in param_parts:
                    if param.startswith("'") and param.endswith("'"):
                        # 字符串字面量
                        params.append(param[1:-1])
                    elif param.startswith('"') and param.endswith('"'):
                        # 字符串字面量
                        params.append(param[1:-1])
                    else:
                        # 字段引用
                        field_value = self._get_field_value_from_context(param, context)
                        params.append(field_value)
            
            # 根据查询名称调用相应的数据库查询
            result = None
            try:
                if query_name == 'get_tax_number_by_name' and len(params) >= 1:
                    result = await DatabaseQueryHelper.get_company_tax_number_by_name(self.db_session, params[0])
                    replacement = f'"{result}"' if result else '""'
                elif query_name == 'get_company_category_by_name' and len(params) >= 1:
                    result = await DatabaseQueryHelper.get_company_category_by_name(self.db_session, params[0])
                    replacement = f'"{result}"' if result else '"GENERAL"'
                elif query_name == 'get_tax_rate_by_category_and_amount' and len(params) >= 2:
                    result = await DatabaseQueryHelper.get_tax_rate_by_category_and_amount(self.db_session, params[0], float(params[1]))
                    replacement = str(result) if result else '0.06'
                else:
                    # 未知查询类型，返回默认值
                    replacement = 'null'
                
                expression = expression.replace(match.group(0), replacement)
                
            except Exception as e:
                print(f"数据库查询执行错误: {query_name} - {str(e)}")
                # 根据查询类型返回默认值
                if 'tax_number' in query_name:
                    replacement = '""'
                elif 'category' in query_name:
                    replacement = '"GENERAL"'
                elif 'tax_rate' in query_name:
                    replacement = '0.06'
                else:
                    replacement = 'null'
                expression = expression.replace(match.group(0), replacement)
        
        return expression
    
    async def _replace_db_query_tax_number(self, expression: str, context: Dict[str, Any]) -> str:
        """替换税号查询函数"""
        import re
        pattern = r'db_query_tax_number_by_name\(([^)]+)\)'
        
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