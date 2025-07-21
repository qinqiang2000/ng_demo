"""
扩展规则引擎 - 支持数据库查询功能
"""
import re
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.domain import InvoiceDomainObject
from ..models.rules import FieldCompletionRule, FieldValidationRule
from ..database.crud import DatabaseQueryHelper
import operator


class DatabaseExpressionEvaluator:
    """支持数据库查询的表达式求值器"""
    
    def __init__(self, db_session: AsyncSession = None):
        self.db_session = db_session
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
    
    async def evaluate(self, expression: str, context: Dict[str, Any]) -> Any:
        """计算表达式"""
        expression = expression.strip()
        
        # 处理数据库查询函数
        if expression.startswith('db_query_'):
            return await self._handle_db_query(expression, context)
        
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
        
        # 处理contains()函数 
        if '.contains(' in expression:
            return await self._handle_contains(expression, context)
        
        # 处理三元表达式 (condition ? value1 : value2)
        if '?' in expression and ':' in expression:
            return await self._handle_ternary(expression, context)
        
        # 处理乘法运算
        if '*' in expression:
            parts = expression.split('*')
            if len(parts) == 2:
                left = await self.evaluate(parts[0].strip(), context)
                right = await self.evaluate(parts[1].strip(), context)
                if isinstance(left, (int, float, Decimal)) and isinstance(right, (int, float, Decimal)):
                    return Decimal(str(left)) * Decimal(str(right))
        
        # 处理减法运算
        if ' - ' in expression:
            parts = expression.split(' - ')
            if len(parts) == 2:
                left = await self.evaluate(parts[0].strip(), context)
                right = await self.evaluate(parts[1].strip(), context)
                if isinstance(left, (int, float, Decimal)) and isinstance(right, (int, float, Decimal)):
                    return Decimal(str(left)) - Decimal(str(right))
        
        # 处理字段访问
        if '.' in expression and not any(op in expression for op in self.operators):
            return self._get_field_value(expression, context)
        
        # 处理比较和逻辑运算
        for op_str, op_func in self.operators.items():
            if f' {op_str} ' in expression:
                parts = expression.split(f' {op_str} ', 1)
                left = await self.evaluate(parts[0].strip(), context)
                right = await self.evaluate(parts[1].strip(), context)
                return op_func(left, right)
        
        # 处理简单变量
        if expression in context:
            return context[expression]
        
        # 处理字段路径
        return self._get_field_value(expression, context)
    
    async def _handle_db_query(self, expression: str, context: Dict[str, Any]) -> Any:
        """处理数据库查询函数"""
        if not self.db_session:
            return None
        
        try:
            # 解析函数调用
            func_name = expression.split('(')[0]
            params_str = expression[expression.index('(') + 1:expression.rindex(')')]
            params = [p.strip().strip('"\'') for p in params_str.split(',')]
            
            # 替换参数中的字段引用
            for i, param in enumerate(params):
                if param.startswith('invoice.'):
                    field_value = self._get_field_value(param, context)
                    if field_value is None:
                        print(f"数据库查询参数为空: {param}")
                        return None
                    params[i] = field_value
            
            # 执行数据库查询
            if func_name == 'db_query_tax_number_by_name':
                if params[0] is None or params[0] == '':
                    return None
                return await DatabaseQueryHelper.get_company_tax_number_by_name(
                    self.db_session, str(params[0])
                )
            elif func_name == 'db_query_tax_rate_by_category_and_amount':
                if params[0] is None or params[0] == '':
                    print(f"企业分类为空，使用默认税率")
                    return 0.06  # 返回默认税率
                try:
                    amount = float(params[1])
                    result = await DatabaseQueryHelper.get_tax_rate_by_category_and_amount(
                        self.db_session, str(params[0]), amount
                    )
                    return result if result is not None else 0.06  # 默认税率
                except (ValueError, TypeError):
                    print(f"金额参数错误: {params[1]}")
                    return 0.06
            elif func_name == 'db_query_company_category_by_name':
                if params[0] is None or params[0] == '':
                    return 'GENERAL'  # 返回默认分类
                result = await DatabaseQueryHelper.get_company_category_by_name(
                    self.db_session, str(params[0])
                )
                return result if result is not None else 'GENERAL'  # 默认分类
            
        except Exception as e:
            print(f"数据库查询错误: {expression} - {str(e)}")
            return None
        
        return None
    
    async def _handle_contains(self, expression: str, context: Dict[str, Any]) -> bool:
        """处理contains函数"""
        try:
            # 解析 field.contains('value') 格式
            parts = expression.split('.contains(')
            field_path = parts[0].strip()
            search_value = parts[1].rstrip(')').strip().strip('"\'')
            
            field_value = self._get_field_value(field_path, context)
            if isinstance(field_value, str):
                return search_value in field_value
            return False
        except:
            return False
    
    async def _handle_ternary(self, expression: str, context: Dict[str, Any]) -> Any:
        """处理三元表达式"""
        try:
            question_pos = expression.index('?')
            colon_pos = expression.index(':', question_pos)
            
            condition = expression[:question_pos].strip()
            true_value = expression[question_pos+1:colon_pos].strip()
            false_value = expression[colon_pos+1:].strip()
            
            condition_result = await self.evaluate(condition, context)
            if condition_result:
                return await self.evaluate(true_value, context)
            else:
                return await self.evaluate(false_value, context)
        except:
            return None
    
    def _has_field(self, field_path: str, context: Dict[str, Any]) -> bool:
        """检查字段是否存在"""
        try:
            value = self._get_field_value(field_path, context)
            return value is not None and value != ""
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
                next_obj = getattr(current, part)
                if next_obj is None:
                    # 创建嵌套对象
                    if part == 'extensions':
                        from types import SimpleNamespace
                        setattr(current, part, SimpleNamespace())
                        next_obj = getattr(current, part)
                current = next_obj
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


class DatabaseFieldCompletionEngine:
    """支持数据库查询的字段补全引擎"""
    
    def __init__(self, db_session: AsyncSession = None):
        self.db_session = db_session
        self.evaluator = DatabaseExpressionEvaluator(db_session)
        self.rules: List[FieldCompletionRule] = []
    
    def load_rules(self, rules: List[FieldCompletionRule]):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    async def complete(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """执行字段补全"""
        context = {'invoice': domain}
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            # 检查应用条件
            if rule.apply_to and not await self.evaluator.evaluate(rule.apply_to, context):
                continue
            
            try:
                # 执行规则表达式
                field_value = await self.evaluator.evaluate(rule.rule_expression, context)
                
                # 设置字段值
                if field_value is not None:
                    self.evaluator._set_field_value(domain, rule.target_field, field_value)
                    print(f"字段补全成功: {rule.rule_name} - {rule.target_field} = {field_value}")
                    
            except Exception as e:
                print(f"字段补全失败: {rule.rule_name} - {str(e)}")
        
        return domain


class DatabaseBusinessValidationEngine:
    """支持数据库查询的业务校验引擎"""
    
    def __init__(self, db_session: AsyncSession = None):
        self.db_session = db_session
        self.evaluator = DatabaseExpressionEvaluator(db_session)
        self.rules: List[FieldValidationRule] = []
    
    def load_rules(self, rules: List[FieldValidationRule]):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    async def validate(self, domain: InvoiceDomainObject) -> Tuple[bool, List[str]]:
        """执行业务校验"""
        context = {'invoice': domain}
        errors = []
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            # 检查应用条件
            if rule.apply_to and not await self.evaluator.evaluate(rule.apply_to, context):
                continue
            
            try:
                # 执行校验规则
                is_valid = await self.evaluator.evaluate(rule.rule_expression, context)
                
                if not is_valid:
                    errors.append(f"{rule.rule_name}: {rule.error_message}")
                    print(f"校验失败: {rule.rule_name} - {rule.error_message}")
                else:
                    print(f"校验通过: {rule.rule_name}")
                    
            except Exception as e:
                errors.append(f"{rule.rule_name}: 规则执行错误 - {str(e)}")
                print(f"校验错误: {rule.rule_name} - {str(e)}")
        
        return len(errors) == 0, errors