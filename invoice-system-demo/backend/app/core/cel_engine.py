"""Google CEL (Common Expression Language) 引擎实现"""
import celpy
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule
from ..database.crud import DatabaseQueryHelper
from .flexible_db_query import FlexibleDatabaseQuery
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
                error_msg = f"{rule.rule_name}: 规则执行错误 - {str(e)}"
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
            # 检查是否包含新的db_query函数
            if 'db_query(' in expression:
                return await self._evaluate_with_flexible_db_queries(expression, context)
            else:
                # 使用标准CEL评估
                return self.evaluate(expression, context)
        except Exception as e:
            print(f"CEL表达式执行错误: {expression} - {str(e)}")
            return None
    
    async def _evaluate_with_flexible_db_queries(self, expression: str, context: Dict[str, Any]) -> Any:
        """处理包含灵活db_query函数的表达式"""
        try:
            # 创建查询处理器
            query_handler = FlexibleDatabaseQuery()
            
            # 处理数据库查询函数
            processed_expression = expression
            
            # 使用正则表达式查找所有db_query调用
            import re
            pattern = r'db_query\(([^)]+)\)'
            
            for match in re.finditer(pattern, expression):
                full_match = match.group(0)
                params_str = match.group(1)
                
                # 解析参数
                params = self._parse_db_query_params(params_str, context)
                if not params:
                    processed_expression = processed_expression.replace(full_match, 'null')
                    continue
                
                # 执行查询
                query_name = params[0]
                query_params = params[1:]
                
                try:
                    result = await query_handler.query(query_name, *query_params)
                    
                    # 根据结果类型转换为CEL字面量
                    if result is None:
                        replacement = 'null'
                    elif isinstance(result, str):
                        replacement = f'"{result}"'
                    elif isinstance(result, (int, float)):
                        replacement = str(result)
                    elif isinstance(result, bool):
                        replacement = 'true' if result else 'false'
                    else:
                        replacement = f'"{str(result)}"'
                    
                    processed_expression = processed_expression.replace(full_match, replacement)
                    
                except Exception as e:
                    print(f"数据库查询执行错误: {query_name} - {str(e)}")
                    processed_expression = processed_expression.replace(full_match, 'null')
            
            # 使用CEL计算处理后的表达式
            return self.evaluate(processed_expression, context)
            
        except Exception as e:
            print(f"灵活数据库查询表达式处理错误: {expression} - {str(e)}")
            return None
    
    def _parse_db_query_params(self, params_str: str, context: Dict[str, Any]) -> Optional[List[str]]:
        """解析db_query函数的参数"""
        try:
            params = []
            current_param = ''
            in_quotes = False
            quote_char = None
            
            for char in params_str:
                if char in ['"', "'"] and not in_quotes:
                    in_quotes = True
                    quote_char = char
                elif char == quote_char and in_quotes:
                    in_quotes = False
                    quote_char = None
                elif char == ',' and not in_quotes:
                    params.append(current_param.strip())
                    current_param = ''
                    continue
                current_param += char
                
            if current_param:
                params.append(current_param.strip())
            
            # 处理参数值
            processed_params = []
            for param in params:
                param = param.strip()
                if param.startswith('"') and param.endswith('"'):
                    # 字符串字面量
                    processed_params.append(param[1:-1])
                elif param.startswith("'") and param.endswith("'"):
                    # 字符串字面量
                    processed_params.append(param[1:-1])
                elif param.startswith('invoice.'):
                    # 字段引用
                    field_value = self._get_field_value_from_context(param, context)
                    processed_params.append(field_value)
                else:
                    # 数字或其他字面量
                    try:
                        # 尝试转换为数字
                        if '.' in param:
                            processed_params.append(float(param))
                        else:
                            processed_params.append(int(param))
                    except ValueError:
                        # 作为字符串处理
                        processed_params.append(param)
            
            return processed_params
            
        except Exception as e:
            print(f"参数解析错误: {params_str} - {str(e)}")
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
                # 不记录未激活的规则日志
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = await self.evaluator.evaluate_async(rule.apply_to, context)
                    if not should_apply:
                        log_entry = {
                            "type": "validation",
                            "status": "skipped",
                            "rule_name": rule.rule_name,
                            "reason": "condition_not_met",
                            "condition": rule.apply_to,
                            "message": f"规则跳过: {rule.rule_name} - 条件不满足: {rule.apply_to}"
                        }
                        self.execution_log.append(log_entry)
                        print(log_entry["message"])
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