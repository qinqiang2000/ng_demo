"""规则引擎实现 - 支持Google CEL和简化版表达式"""
import re
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple, Callable
from datetime import datetime
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule, FieldValidationRule
from .cel_engine import CELFieldCompletionEngine, CELBusinessValidationEngine, CELExpressionEvaluator
import operator


class SimpleExpressionEvaluator:
    """简化版表达式求值器"""
    
    def __init__(self):
        self.operators = {
            '==': operator.eq,
            '!=': operator.ne,
            '>': operator.gt,
            '>=': operator.ge,
            '<': operator.lt,
            '<=': operator.le,
            '&&': lambda x, y: bool(x) and bool(y),
            '||': lambda x, y: bool(x) or bool(y),
        }
        # 注册的函数
        self.functions: Dict[str, Callable] = {}
    
    def evaluate(self, expression: str, context: Dict[str, Any]) -> Any:
        """计算表达式"""
        expression = expression.strip()
        
        # 处理字符串字面量
        if expression.startswith('"') and expression.endswith('"'):
            return expression[1:-1]
        if expression.startswith("'") and expression.endswith("'"):
            return expression[1:-1]
        
        # 处理数字字面量
        try:
            if '.' in expression:
                return Decimal(expression)
            return int(expression)
        except (ValueError, Exception):
            pass
        
        # 处理布尔值
        if expression.lower() == 'true':
            return True
        if expression.lower() == 'false':
            return False
        
        # 处理逻辑运算符 - 优先级最低，最先处理
        # 1. 处理逻辑OR (||) - 优先级最低
        if ' || ' in expression:
            parts = expression.split(' || ', 1)
            if len(parts) == 2:
                left = self.evaluate(parts[0].strip(), context)
                right = self.evaluate(parts[1].strip(), context)
                return bool(left) or bool(right)
        
        # 2. 处理逻辑AND (&&) - 中等优先级
        if ' && ' in expression:
            parts = expression.split(' && ', 1)
            if len(parts) == 2:
                left = self.evaluate(parts[0].strip(), context)
                right = self.evaluate(parts[1].strip(), context)
                return bool(left) and bool(right)
        
        # 3. 处理比较运算符 - 较高优先级
        for op_str in ['==', '!=', '>=', '<=', '>', '<']:
            if f' {op_str} ' in expression:
                parts = expression.split(f' {op_str} ', 1)
                if len(parts) == 2:
                    left = self.evaluate(parts[0].strip(), context)
                    right = self.evaluate(parts[1].strip(), context)
                    op_func = self.operators[op_str]
                    
                    # 处理None值比较
                    if left is None or right is None:
                        if op_str == '==':
                            return left == right
                        elif op_str == '!=':
                            return left != right
                        elif op_str in ['>', '>=', '<', '<=']:
                            return False  # None值的数值比较返回False
                    
                    return op_func(left, right)
        
        # 处理否定运算符 (在逻辑运算符之后处理，确保!has()能正确工作)
        if expression.startswith('!'):
            inner_expression = expression[1:].strip()
            print(f"[DEBUG] 处理否定运算符: 原表达式='{expression}', 内部表达式='{inner_expression}'")
            # 递归求值内部表达式
            inner_result = self.evaluate(inner_expression, context)
            result = not bool(inner_result)
            print(f"[DEBUG] 否定运算: !({inner_expression}) = !({inner_result}) = {result}")
            return result
        
        # 处理has()函数
        if expression.startswith('has(') and expression.endswith(')'):
            field_path = expression[4:-1].strip()
            return self._has_field(field_path, context)
        
        # 处理注册的函数调用
        func_match = re.match(r'(\w+)\((.*)\)', expression)
        if func_match:
            func_name = func_match.group(1)
            args_str = func_match.group(2)
            
            if func_name in self.functions:
                # 解析参数
                args = self._parse_function_args(args_str, context)
                return self.functions[func_name](*args)
        
        # 处理乘法运算 (用于税额计算)
        if '*' in expression:
            parts = expression.split('*')
            if len(parts) == 2:
                left = self.evaluate(parts[0].strip(), context)
                right = self.evaluate(parts[1].strip(), context)
                if isinstance(left, (int, float, Decimal)) and isinstance(right, (int, float, Decimal)):
                    return Decimal(str(left)) * Decimal(str(right))
        
        # 处理减法运算 (用于净额计算)
        if ' - ' in expression:
            parts = expression.split(' - ')
            if len(parts) == 2:
                left = self.evaluate(parts[0].strip(), context)
                right = self.evaluate(parts[1].strip(), context)
                if isinstance(left, (int, float, Decimal)) and isinstance(right, (int, float, Decimal)):
                    return Decimal(str(left)) - Decimal(str(right))
        
        # 处理字段访问
        if '.' in expression and not any(op in expression for op in self.operators):
            return self._get_field_value_from_context(expression, context)
        
        # 处理逻辑运算符 - 按优先级顺序处理 (OR优先级最低，最后处理)
        # 1. 处理逻辑OR (||) - 优先级最低
        if ' || ' in expression:
            parts = expression.split(' || ', 1)
            if len(parts) == 2:
                left = self.evaluate(parts[0].strip(), context)
                right = self.evaluate(parts[1].strip(), context)
                return bool(left) or bool(right)
        
        # 2. 处理逻辑AND (&&) - 优先级中等
        if ' && ' in expression:
            parts = expression.split(' && ', 1)
            if len(parts) == 2:
                left = self.evaluate(parts[0].strip(), context)
                right = self.evaluate(parts[1].strip(), context)
                return bool(left) and bool(right)
        
        # 3. 处理比较运算符 - 优先级较高
        for op_str in ['==', '!=', '>=', '<=', '>', '<']:
            if f' {op_str} ' in expression:
                parts = expression.split(f' {op_str} ', 1)
                if len(parts) == 2:
                    left = self.evaluate(parts[0].strip(), context)
                    right = self.evaluate(parts[1].strip(), context)
                    op_func = self.operators[op_str]
                    
                    # 处理None值比较
                    if left is None or right is None:
                        if op_str == '==':
                            return left == right
                        elif op_str == '!=':
                            return left != right
                        elif op_str in ['>', '>=', '<', '<=']:
                            return False  # None值的数值比较返回False
                    
                    return op_func(left, right)
        
        
        # 处理简单变量
        if expression in context:
            return context[expression]
        
        # 处理字段路径
        return self._get_field_value_from_context(expression, context)
    
    def _has_field(self, field_path: str, context: Dict[str, Any]) -> bool:
        """检查字段是否存在且有值"""
        print(f"[DEBUG] _has_field 检查: field_path='{field_path}'")
        try:
            value = self._get_field_value_from_context(field_path, context)
            print(f"[DEBUG] _has_field 获取到的值: {value} (type: {type(value)})")
            
            # None值视为不存在
            if value is None:
                print(f"[DEBUG] _has_field 结果: False (值为None)")
                return False
            # 空字符串视为不存在
            if isinstance(value, str) and value == "":
                print(f"[DEBUG] _has_field 结果: False (值为空字符串)")
                return False
            # 数值0视为存在（只有None才视为不存在）
            # if isinstance(value, (int, float, Decimal)) and value == 0:
            #     return False
            print(f"[DEBUG] _has_field 结果: True (值存在且非空)")
            return True
        except Exception as e:
            print(f"[DEBUG] _has_field 异常: {str(e)}")
            return False
    
    def _get_field_value_from_context(self, field_path: str, context: Dict[str, Any]) -> Any:
        """从context中获取字段值"""
        print(f"[DEBUG] _get_field_value_from_context: field_path='{field_path}', context_keys={list(context.keys())}")
        
        parts = field_path.split('.')
        current = context
        
        for i, part in enumerate(parts):
            print(f"[DEBUG] 处理字段部分 {i}: '{part}', 当前对象类型: {type(current).__name__}")
            
            if isinstance(current, dict):
                current = current.get(part)
                print(f"[DEBUG] 从字典获取 '{part}': {current}")
            elif hasattr(current, part):
                current = getattr(current, part)
                print(f"[DEBUG] 从对象获取属性 '{part}': {current} (type: {type(current)})")
            else:
                print(f"[DEBUG] 字段 '{part}' 不存在于 {type(current).__name__}")
                return None
            
            if current is None:
                print(f"[DEBUG] 字段值为None，返回None")
                return None
        
        print(f"[DEBUG] 最终获取到的值: {current} (type: {type(current)})")
        return current
    
    def _get_field_value(self, obj_or_path: Any, field_path: Optional[str] = None) -> Any:
        """获取字段值
        
        Args:
            obj_or_path: 对象或字段路径字符串
            field_path: 可选的字段路径，当第一个参数是对象时使用
        """
        # 如果第一个参数是字符串，表示从context中获取
        if isinstance(obj_or_path, str) and field_path is None:
            # 旧的调用方式：_get_field_value(field_path, context)
            # 这种情况下obj_or_path实际上是field_path
            return None  # 需要context参数
        
        # 如果有两个参数，第一个是对象，第二个是字段路径
        if field_path is not None:
            obj = obj_or_path
            parts = field_path.split('.')
        else:
            # 单参数调用，obj_or_path就是要访问的对象
            if not hasattr(obj_or_path, '__dict__') and not isinstance(obj_or_path, dict):
                return obj_or_path
            obj = obj_or_path
            parts = []
        
        current = obj
        for part in parts:
            if isinstance(current, dict):
                current = current.get(part)
            elif hasattr(current, part):
                current = getattr(current, part)
            else:
                return None
            
            if current is None:
                return None
        
        return current
    
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
            setattr(current, parts[-1], value)
            return True
        elif isinstance(current, dict):
            current[parts[-1]] = value
            return True
        
        return False
    
    def register_function(self, name: str, func: Callable):
        """注册函数供表达式使用"""
        self.functions[name] = func
    
    def _parse_function_args(self, args_str: str, context: Dict[str, Any]) -> List[Any]:
        """解析函数参数"""
        if not args_str.strip():
            return []
        
        args = []
        # 简单的参数解析，支持逗号分隔
        parts = args_str.split(',')
        for part in parts:
            part = part.strip()
            # 递归计算每个参数
            arg_value = self.evaluate(part, context)
            args.append(arg_value)
        
        return args


class FieldCompletionEngine:
    """字段补全引擎"""
    
    def __init__(self):
        self.evaluator = SimpleExpressionEvaluator()
        self.rules: List[FieldCompletionRule] = []
        self.execution_log: List[Dict] = []
        self._register_builtin_functions()
    
    def _register_builtin_functions(self):
        """注册内置函数"""
        # 注册API调用函数
        from ..services.product_api_service import product_api
        
        # 注册获取标准名称的函数
        def get_standard_name(description: str) -> str:
            return product_api.get_standard_name(description)
        
        # 注册获取税率的函数
        def get_tax_rate(description: str) -> float:
            return product_api.get_tax_rate(description)
        
        # 注册获取税种的函数
        def get_tax_category(description: str) -> str:
            return product_api.get_tax_category(description)
        
        self.evaluator.register_function('get_standard_name', get_standard_name)
        self.evaluator.register_function('get_tax_rate', get_tax_rate)
        self.evaluator.register_function('get_tax_category', get_tax_category)
    
    def load_rules(self, rules: List[FieldCompletionRule]):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def complete(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """执行字段补全"""
        context = {'invoice': domain}
        self.execution_log = []  # 重置执行日志
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            # 检查是否是针对items的规则
            if rule.target_field.startswith('items[].'):
                # 处理items列表中的每个项目
                item_field = rule.target_field[8:]  # 去掉 'items[].'
                for idx, item in enumerate(domain.items):
                    item_context = {'invoice': domain, 'item': item}
                    
                    # 检查应用条件
                    if rule.apply_to and not self.evaluator.evaluate(rule.apply_to, item_context):
                        continue
                    
                    try:
                        # 根据规则类型决定是否执行
                        rule_type = getattr(rule, 'rule_type', 'DEFAULT')
                        if rule_type == 'DEFAULT':
                            # DEFAULT类型：仅在字段为空时设置
                            current_value = self.evaluator._get_field_value(item, item_field)
                            if current_value is not None and current_value != "" and current_value != 0:
                                continue  # 字段已有值，跳过
                        
                        # 执行规则表达式
                        field_value = self.evaluator.evaluate(rule.rule_expression, item_context)
                        
                        # 设置字段值
                        if field_value is not None:
                            self.evaluator._set_field_value(item, item_field, field_value)
                            log_entry = {
                                "rule_name": rule.rule_name,
                                "target_field": f"items[{idx}].{item_field}",
                                "value": str(field_value),
                                "status": "success"
                            }
                            self.execution_log.append(log_entry)
                            print(f"字段补全成功: {rule.rule_name} - items[{idx}].{item_field} = {field_value}")
                            
                    except Exception as e:
                        log_entry = {
                            "rule_name": rule.rule_name,
                            "target_field": f"items[{idx}].{item_field}",
                            "error_message": str(e),
                            "status": "error"
                        }
                        self.execution_log.append(log_entry)
                        print(f"字段补全失败: {rule.rule_name} - {str(e)}")
            else:
                # 处理普通字段
                # 检查应用条件
                if rule.apply_to and not self.evaluator.evaluate(rule.apply_to, context):
                    continue
                
                try:
                    # 根据规则类型决定是否执行
                    rule_type = getattr(rule, 'rule_type', 'DEFAULT')
                    if rule_type == 'DEFAULT':
                        # DEFAULT类型：仅在字段为空时设置
                        current_value = self.evaluator._get_field_value(domain, rule.target_field)
                        if current_value is not None and current_value != "" and current_value != 0:
                            continue  # 字段已有值，跳过
                    
                    # 执行规则表达式
                    field_value = self.evaluator.evaluate(rule.rule_expression, context)
                    
                    # 设置字段值
                    if field_value is not None:
                        self.evaluator._set_field_value(domain, rule.target_field, field_value)
                        log_entry = {
                            "rule_name": rule.rule_name,
                            "target_field": rule.target_field,
                            "value": str(field_value),
                            "status": "success"
                        }
                        self.execution_log.append(log_entry)
                        print(f"字段补全成功: {rule.rule_name} - {rule.target_field} = {field_value}")
                        
                except Exception as e:
                    log_entry = {
                        "rule_name": rule.rule_name,
                        "target_field": rule.target_field,
                        "error_message": str(e),
                        "status": "error"
                    }
                    self.execution_log.append(log_entry)
                    print(f"字段补全失败: {rule.rule_name} - {str(e)}")
        
        return domain


class BusinessValidationEngine:
    """业务校验引擎"""
    
    def __init__(self):
        self.evaluator = SimpleExpressionEvaluator()
        self.rules: List[FieldValidationRule] = []
        self.execution_log: List[Dict] = []
    
    def load_rules(self, rules: List[FieldValidationRule]):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def validate(self, domain: InvoiceDomainObject) -> Tuple[bool, List[str]]:
        """执行业务校验"""
        context = {'invoice': domain}
        errors = []
        self.execution_log = []  # 重置执行日志
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            # 检查应用条件
            if rule.apply_to and not self.evaluator.evaluate(rule.apply_to, context):
                continue
            
            try:
                # 执行校验规则
                is_valid = self.evaluator.evaluate(rule.rule_expression, context)
                
                if not is_valid:
                    log_entry = {
                        "rule_name": rule.rule_name,
                        "error_message": rule.error_message,
                        "status": "failed"
                    }
                    self.execution_log.append(log_entry)
                    errors.append(f"{rule.rule_name}: {rule.error_message}")
                    print(f"校验失败: {rule.rule_name} - {rule.error_message}")
                else:
                    log_entry = {
                        "rule_name": rule.rule_name,
                        "status": "passed"
                    }
                    self.execution_log.append(log_entry)
                    print(f"校验通过: {rule.rule_name}")
                    
            except Exception as e:
                log_entry = {
                    "rule_name": rule.rule_name,
                    "error_message": f"规则执行错误 - {str(e)}",
                    "status": "error"
                }
                self.execution_log.append(log_entry)
                errors.append(f"{rule.rule_name}: 规则执行错误 - {str(e)}")
                print(f"校验错误: {rule.rule_name} - {str(e)}")
        
        return len(errors) == 0, errors


# CEL引擎别名，供外部使用
FieldCompletionEngine = CELFieldCompletionEngine
BusinessValidationEngine = CELBusinessValidationEngine