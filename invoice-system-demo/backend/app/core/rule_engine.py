"""规则引擎实现 - 支持Google CEL和简化版表达式"""
import re
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple
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
            'AND': operator.and_,
            'OR': operator.or_,
        }
    
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
        
        # 处理has()函数
        if expression.startswith('has(') and expression.endswith(')'):
            field_path = expression[4:-1].strip()
            return self._has_field(field_path, context)
        
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
            return self._get_field_value(expression, context)
        
        # 处理比较和逻辑运算
        for op_str, op_func in self.operators.items():
            if f' {op_str} ' in expression:
                parts = expression.split(f' {op_str} ', 1)
                left = self.evaluate(parts[0].strip(), context)
                right = self.evaluate(parts[1].strip(), context)
                return op_func(left, right)
        
        # 处理简单变量
        if expression in context:
            return context[expression]
        
        # 处理字段路径
        return self._get_field_value(expression, context)
    
    def _has_field(self, field_path: str, context: Dict[str, Any]) -> bool:
        """检查字段是否存在"""
        try:
            value = self._get_field_value(field_path, context)
            return value is not None
        except:
            return False
    
    def _get_field_value(self, field_path: str, context: Dict[str, Any]) -> Any:
        """获取字段值"""
        parts = field_path.split('.')
        current = context
        
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


class FieldCompletionEngine:
    """字段补全引擎"""
    
    def __init__(self):
        self.evaluator = SimpleExpressionEvaluator()
        self.rules: List[FieldCompletionRule] = []
    
    def load_rules(self, rules: List[FieldCompletionRule]):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def complete(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """执行字段补全"""
        context = {'invoice': domain}
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            # 检查应用条件
            if rule.apply_to and not self.evaluator.evaluate(rule.apply_to, context):
                continue
            
            try:
                # 执行规则表达式
                field_value = self.evaluator.evaluate(rule.rule_expression, context)
                
                # 设置字段值
                if field_value is not None:
                    self.evaluator._set_field_value(domain, rule.target_field, field_value)
                    print(f"字段补全成功: {rule.rule_name} - {rule.target_field} = {field_value}")
                    
            except Exception as e:
                print(f"字段补全失败: {rule.rule_name} - {str(e)}")
        
        return domain


class BusinessValidationEngine:
    """业务校验引擎"""
    
    def __init__(self):
        self.evaluator = SimpleExpressionEvaluator()
        self.rules: List[FieldValidationRule] = []
    
    def load_rules(self, rules: List[FieldValidationRule]):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def validate(self, domain: InvoiceDomainObject) -> Tuple[bool, List[str]]:
        """执行业务校验"""
        context = {'invoice': domain}
        errors = []
        
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
                    errors.append(f"{rule.rule_name}: {rule.error_message}")
                    print(f"校验失败: {rule.rule_name} - {rule.error_message}")
                else:
                    print(f"校验通过: {rule.rule_name}")
                    
            except Exception as e:
                errors.append(f"{rule.rule_name}: 规则执行错误 - {str(e)}")
                print(f"校验错误: {rule.rule_name} - {str(e)}")
        
        return len(errors) == 0, errors


# CEL引擎别名，供外部使用
FieldCompletionEngine = CELFieldCompletionEngine
BusinessValidationEngine = CELBusinessValidationEngine