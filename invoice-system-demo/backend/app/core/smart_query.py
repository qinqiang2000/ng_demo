"""
智能数据库查询解析器
支持极简语法: db.table.field[conditions]
"""
import re
from typing import Dict, Any, List, Tuple, Optional, Union
from sqlalchemy import text, select, and_, or_, Column, Table
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.sql import operators
from decimal import Decimal
import logging

logger = logging.getLogger(__name__)


class SmartQueryParser:
    """智能查询语法解析器"""
    
    # 表名映射（从数据库模型映射到实际表名）
    TABLE_MAPPING = {
        'companies': 'companies',
        'tax_rates': 'tax_rates',
        'business_rules': 'business_rules'
    }
    
    # 字段名映射（处理特殊的字段名映射）
    FIELD_MAPPING = {
        'companies': {
            'name': 'name',
            'tax_number': 'tax_number',
            'category': 'category',
            'address': 'address',
            'phone': 'phone',
            'email': 'email'
        },
        'tax_rates': {
            'rate': 'rate',
            'category': 'category',
            'min_amount': 'min_amount',
            'max_amount': 'max_amount'
        }
    }
    
    # 支持的操作符映射
    OPERATORS = {
        '=': operators.eq,
        '==': operators.eq,
        '!=': operators.ne,
        '>': operators.gt,
        '>=': operators.ge,
        '<': operators.lt,
        '<=': operators.le,
        'IN': operators.in_op,
        'NOT IN': operators.notin_op,
        'LIKE': operators.like_op,
        'ILIKE': operators.ilike_op,
    }
    
    def __init__(self):
        # 查询语法正则表达式
        # 匹配: db.table.field[conditions] 或 db.table[conditions]
        self.query_pattern = re.compile(
            r'db\.(\w+)(?:\.(\w+))?\[(.*?)\]'
        )
        
        # 条件解析正则
        self.condition_pattern = re.compile(
            r'(\w+)\s*(=|==|!=|>|>=|<|<=|IN|NOT IN|LIKE|ILIKE)\s*(.+?)(?:\s*,\s*|$)'
        )
        
    def parse(self, expression: str) -> Dict[str, Any]:
        """
        解析查询表达式
        
        Args:
            expression: 查询表达式，如 db.companies.tax_number[name=invoice.supplier.name]
            
        Returns:
            解析后的查询结构
        """
        match = self.query_pattern.match(expression.strip())
        if not match:
            raise ValueError(f"无效的查询语法: {expression}")
            
        table_name = match.group(1)
        field_name = match.group(2)
        conditions_str = match.group(3)
        
        # 如果没有指定字段，默认返回所有字段
        if not field_name:
            field_name = '*'
            
        # 解析条件
        conditions = self._parse_conditions(conditions_str)
        
        return {
            'table': table_name,
            'select': field_name,
            'where': conditions,
            'limit': 1  # 默认只返回一条记录
        }
        
    def _parse_conditions(self, conditions_str: str) -> List[Dict[str, Any]]:
        """解析查询条件"""
        if not conditions_str.strip():
            return []
            
        conditions = []
        
        # 使用更灵活的解析方式处理复杂条件  
        parts = self._split_conditions(conditions_str)
        
        for part in parts:
            condition = self._parse_single_condition(part.strip())
            if condition:
                conditions.append(condition)
                
        return conditions
        
    def _split_conditions(self, conditions_str: str) -> List[str]:
        """智能分割条件，处理嵌套的函数调用等"""
        parts = []
        current = []
        paren_depth = 0
        in_string = False
        string_char = None
        
        for char in conditions_str:
            if char in ('"', "'") and not in_string:
                in_string = True
                string_char = char
            elif char == string_char and in_string:
                in_string = False
                string_char = None
            elif char == '(' and not in_string:
                paren_depth += 1
            elif char == ')' and not in_string:
                paren_depth -= 1
            elif char == ',' and paren_depth == 0 and not in_string:
                parts.append(''.join(current))
                current = []
                continue
                
            current.append(char)
            
        if current:
            parts.append(''.join(current))
            
        return parts
        
    def _parse_single_condition(self, condition_str: str) -> Optional[Dict[str, Any]]:
        """解析单个条件"""
        # 按照操作符长度排序，优先匹配长的操作符（如 >= 先于 >）
        sorted_operators = sorted(self.OPERATORS.items(), key=lambda x: len(x[0]), reverse=True)
        
        # 尝试使用正则匹配
        for op_text, op_func in sorted_operators:
            # 转义特殊字符
            op_pattern = re.escape(op_text)
            pattern = rf'(\w+)\s*{op_pattern}\s*(.+)'
            match = re.match(pattern, condition_str.strip())
            if match:
                field = match.group(1)
                value = match.group(2).strip()
                
                # 处理值类型
                value = self._parse_value(value)
                
                return {
                    'field': field,
                    'operator': op_text,
                    'value': value
                }
                
        # 处理特殊情况：BETWEEN
        between_match = re.match(r'(\w+)\s+BETWEEN\s+(.+?)\s+AND\s+(.+)', condition_str, re.IGNORECASE)
        if between_match:
            field = between_match.group(1)
            min_val = self._parse_value(between_match.group(2))
            max_val = self._parse_value(between_match.group(3))
            return {
                'field': field,
                'operator': 'BETWEEN',
                'value': (min_val, max_val)
            }
            
        logger.warning(f"无法解析条件: {condition_str}")
        return None
        
    def _parse_value(self, value_str: str) -> Any:
        """解析值的类型"""
        value_str = value_str.strip()
        
        # 移除引号
        if (value_str.startswith('"') and value_str.endswith('"')) or \
           (value_str.startswith("'") and value_str.endswith("'")):
            return value_str[1:-1]
            
        # 尝试解析为数字
        if not any(c.isalpha() or c in ['$', '.'] for c in value_str if c not in ['-', '+']):
            try:
                if '.' in value_str:
                    return float(value_str)  # 使用float而不是Decimal，避免转换错误
                else:
                    return int(value_str)
            except ValueError:
                pass
            
        # 布尔值
        if value_str.lower() in ('true', 'false'):
            return value_str.lower() == 'true'
            
        # NULL值
        if value_str.lower() in ('null', 'none'):
            return None
            
        # 列表值（用于IN操作）
        if value_str.startswith('[') and value_str.endswith(']'):
            items = value_str[1:-1].split(',')
            return [self._parse_value(item.strip()) for item in items]
            
        # 默认作为字符串或表达式（包含变量引用）
        return value_str


class SmartQueryExecutor:
    """智能查询执行器"""
    
    def __init__(self, db_session: AsyncSession):
        self.db_session = db_session
        self.parser = SmartQueryParser()
        
    async def execute(self, expression: str, context: Dict[str, Any]) -> Any:
        """
        执行查询表达式
        
        Args:
            expression: 查询表达式
            context: 上下文变量（用于替换表达式中的变量）
            
        Returns:
            查询结果
        """
        try:
            # 解析表达式
            query_info = self.parser.parse(expression)
            
            # 构建SQL查询
            sql = self._build_sql(query_info, context)
            
            # 执行查询
            result = await self.db_session.execute(sql)
            row = result.first()
            
            if not row:
                return None
                
            # 如果只查询一个字段，直接返回值
            if query_info['select'] != '*' and len(row) == 1:
                return row[0]
            else:
                # 返回字典格式
                return dict(row)
                
        except Exception as e:
            logger.error(f"查询执行失败: {expression}, 错误: {e}")
            raise
            
    def _build_sql(self, query_info: Dict[str, Any], context: Dict[str, Any]) -> Any:
        """构建SQL查询"""
        table = query_info['table']
        select_field = query_info['select']
        conditions = query_info['where']
        
        # 构建SELECT子句
        if select_field == '*':
            sql = f"SELECT * FROM {table}"
        else:
            sql = f"SELECT {select_field} FROM {table}"
            
        # 构建WHERE子句
        if conditions:
            where_parts = []
            params = {}
            
            for idx, condition in enumerate(conditions):
                field = condition['field']
                operator = condition['operator']
                value = condition['value']
                
                # 处理值替换
                if isinstance(value, str) and value.startswith('$'):
                    # 从上下文中获取值
                    var_path = value[1:]  # 去掉$
                    value = self._get_value_from_context(var_path, context)
                elif isinstance(value, str) and '.' in value:
                    # 可能是路径表达式
                    try:
                        value = self._get_value_from_context(value, context)
                    except:
                        pass  # 保持原值
                        
                # 构建条件
                param_name = f"param_{idx}"
                
                if operator == 'BETWEEN':
                    where_parts.append(f"{field} BETWEEN :{param_name}_min AND :{param_name}_max")
                    params[f"{param_name}_min"] = value[0]
                    params[f"{param_name}_max"] = value[1]
                elif operator in ('IN', 'NOT IN'):
                    where_parts.append(f"{field} {operator} :{param_name}")
                    params[param_name] = value
                else:
                    where_parts.append(f"{field} {operator} :{param_name}")
                    params[param_name] = value
                    
            sql += " WHERE " + " AND ".join(where_parts)
            
        # 添加LIMIT
        sql += f" LIMIT {query_info['limit']}"
        
        return text(sql).bindparams(**params)
        
    def _get_value_from_context(self, path: str, context: Dict[str, Any]) -> Any:
        """从上下文中获取值"""
        parts = path.split('.')
        value = context
        
        for part in parts:
            if isinstance(value, dict):
                value = value.get(part)
            else:
                value = getattr(value, part, None)
                
            if value is None:
                break
                
        return value


# 便捷函数
async def smart_query(expression: str, context: Dict[str, Any], db_session: AsyncSession) -> Any:
    """
    执行智能查询
    
    Args:
        expression: 查询表达式，如 db.companies.tax_number[name=invoice.supplier.name]
        context: 上下文变量
        db_session: 数据库会话
        
    Returns:
        查询结果
    """
    executor = SmartQueryExecutor(db_session)
    return await executor.execute(expression, context)