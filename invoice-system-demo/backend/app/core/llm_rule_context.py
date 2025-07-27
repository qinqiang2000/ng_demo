"""LLM Rule Generation Context Schema

This module defines the structured context for LLM-based rule generation.
It provides minimal yet comprehensive information assuming basic CEL/UBL knowledge.
"""

from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field
from enum import Enum


class RuleType(str, Enum):
    """Types of rules that can be generated"""
    COMPLETION = "completion"
    VALIDATION = "validation"


class FieldType(str, Enum):
    """Domain object field types"""
    STRING = "string"
    NUMBER = "number"
    DECIMAL = "decimal"
    DATE = "date"
    BOOLEAN = "boolean"
    OBJECT = "object"
    LIST = "list"
    DICT = "dict"


class DomainFieldInfo(BaseModel):
    """Information about a domain object field"""
    name: str
    path: str
    type: FieldType
    nullable: bool = True
    description: Optional[str] = None
    example: Optional[Any] = None


class DatabaseTableInfo(BaseModel):
    """Database table schema information"""
    name: str
    fields: List[str]
    key_field: str
    description: str


class FunctionSignature(BaseModel):
    """Function signature for available functions"""
    name: str
    parameters: List[str]
    return_type: str
    description: str
    example: str


class RulePattern(BaseModel):
    """Common rule pattern for reference"""
    name: str
    type: RuleType
    description: str
    template: str
    example: str


class SmartQuerySyntax(BaseModel):
    """Smart query syntax reference"""
    pattern: str = "db.table.field[conditions]"
    operators: List[str] = ["=", "!=", ">", ">=", "<", "<=", "IN", "NOT IN", "LIKE", "BETWEEN"]
    examples: List[str] = Field(default_factory=list)


class LLMRuleContext(BaseModel):
    """Complete context for LLM rule generation"""
    rule_type: RuleType
    target_field: Optional[str] = None
    
    # Domain model reference
    domain_fields: List[DomainFieldInfo] = Field(default_factory=list)
    
    # Database schema
    database_tables: List[DatabaseTableInfo] = Field(default_factory=list)
    
    # Available functions
    cel_functions: List[FunctionSignature] = Field(default_factory=list)
    product_api_functions: List[FunctionSignature] = Field(default_factory=list)
    
    # Syntax references
    smart_query_syntax: SmartQuerySyntax = Field(default_factory=SmartQuerySyntax)
    
    # Common patterns
    rule_patterns: List[RulePattern] = Field(default_factory=list)
    
    # Context-specific hints
    hints: List[str] = Field(default_factory=list)


# Pre-defined domain field information
INVOICE_DOMAIN_FIELDS = [
    # Basic fields
    DomainFieldInfo(
        name="invoice_number",
        path="invoice.invoice_number",
        type=FieldType.STRING,
        nullable=False,
        description="发票号码"
    ),
    DomainFieldInfo(
        name="issue_date",
        path="invoice.issue_date",
        type=FieldType.DATE,
        nullable=False,
        description="开票日期"
    ),
    DomainFieldInfo(
        name="invoice_type",
        path="invoice.invoice_type",
        type=FieldType.STRING,
        description="发票类型"
    ),
    DomainFieldInfo(
        name="country",
        path="invoice.country",
        type=FieldType.STRING,
        description="国家代码",
        example="CN"
    ),
    
    # Amount fields
    DomainFieldInfo(
        name="total_amount",
        path="invoice.total_amount",
        type=FieldType.DECIMAL,
        nullable=False,
        description="总金额"
    ),
    DomainFieldInfo(
        name="tax_amount",
        path="invoice.tax_amount",
        type=FieldType.DECIMAL,
        description="税额"
    ),
    DomainFieldInfo(
        name="net_amount",
        path="invoice.net_amount",
        type=FieldType.DECIMAL,
        description="净额"
    ),
    
    # Party fields
    DomainFieldInfo(
        name="supplier.name",
        path="invoice.supplier.name",
        type=FieldType.STRING,
        nullable=False,
        description="供应商名称"
    ),
    DomainFieldInfo(
        name="supplier.tax_no",
        path="invoice.supplier.tax_no",
        type=FieldType.STRING,
        description="供应商税号"
    ),
    DomainFieldInfo(
        name="supplier.email",
        path="invoice.supplier.email",
        type=FieldType.STRING,
        description="供应商邮箱"
    ),
    DomainFieldInfo(
        name="customer.name",
        path="invoice.customer.name",
        type=FieldType.STRING,
        nullable=False,
        description="客户名称"
    ),
    DomainFieldInfo(
        name="customer.tax_no",
        path="invoice.customer.tax_no",
        type=FieldType.STRING,
        description="客户税号"
    ),
    
    # Items
    DomainFieldInfo(
        name="items",
        path="invoice.items",
        type=FieldType.LIST,
        description="发票项目列表"
    ),
    
    # Extensions
    DomainFieldInfo(
        name="extensions",
        path="invoice.extensions",
        type=FieldType.DICT,
        description="扩展字段字典"
    ),
]

# Pre-defined database tables
DATABASE_TABLES = [
    DatabaseTableInfo(
        name="companies",
        fields=["name", "tax_number", "category", "address", "phone", "email"],
        key_field="name",
        description="企业信息表"
    ),
    DatabaseTableInfo(
        name="tax_rates",
        fields=["rate", "category", "min_amount", "max_amount"],
        key_field="category",
        description="税率配置表"
    ),
    DatabaseTableInfo(
        name="business_rules",
        fields=["rule_id", "rule_name", "rule_type", "rule_expression"],
        key_field="rule_id",
        description="业务规则表"
    ),
]

# CEL built-in functions
CEL_FUNCTIONS = [
    FunctionSignature(
        name="has",
        parameters=["field"],
        return_type="bool",
        description="检查字段是否存在且非null",
        example="has(invoice.tax_amount)"
    ),
    FunctionSignature(
        name="matches",
        parameters=["string", "pattern"],
        return_type="bool",
        description="正则表达式匹配",
        example="invoice.supplier.tax_no.matches('^[0-9]{15}[A-Z0-9]{3}$')"
    ),
    FunctionSignature(
        name="size",
        parameters=["list_or_string"],
        return_type="int",
        description="获取列表或字符串长度",
        example="invoice.items.size()"
    ),
    FunctionSignature(
        name="all",
        parameters=["list", "item", "condition"],
        return_type="bool",
        description="检查列表所有元素是否满足条件",
        example="invoice.items.all(item, item.amount > 0)"
    ),
    FunctionSignature(
        name="map",
        parameters=["list", "item", "expression"],
        return_type="list",
        description="映射列表元素",
        example="invoice.items.map(item, item.amount)"
    ),
    FunctionSignature(
        name="reduce",
        parameters=["list", "accumulator", "expression"],
        return_type="any",
        description="归约列表",
        example="invoice.items.map(item, item.amount).reduce(sum, sum + _)"
    ),
]

# Product API functions
PRODUCT_API_FUNCTIONS = [
    FunctionSignature(
        name="get_standard_name",
        parameters=["description"],
        return_type="string",
        description="获取商品标准名称",
        example="get_standard_name(item.description)"
    ),
    FunctionSignature(
        name="get_tax_rate",
        parameters=["description"],
        return_type="decimal",
        description="获取商品税率",
        example="get_tax_rate(item.description)"
    ),
    FunctionSignature(
        name="get_tax_category",
        parameters=["description"],
        return_type="string",
        description="获取商品税种",
        example="get_tax_category(item.description)"
    ),
]


def get_base_context(rule_type: RuleType) -> LLMRuleContext:
    """Get base context for rule generation"""
    context = LLMRuleContext(rule_type=rule_type)
    
    # Add domain fields
    context.domain_fields = INVOICE_DOMAIN_FIELDS
    
    # Add database tables
    context.database_tables = DATABASE_TABLES
    
    # Add functions
    context.cel_functions = CEL_FUNCTIONS
    context.product_api_functions = PRODUCT_API_FUNCTIONS
    
    # Add smart query examples
    context.smart_query_syntax.examples = [
        "db.companies.tax_number[name=invoice.supplier.name]",
        "db.tax_rates.rate[category='GENERAL']",
        "db.companies[name='携程广州']",
        "db.tax_rates.rate[category=$category, min_amount<=$amount, max_amount>=$amount]",
    ]
    
    return context


def get_completion_patterns() -> List[RulePattern]:
    """Get common completion rule patterns"""
    return [
        RulePattern(
            name="数据库查询补全",
            type=RuleType.COMPLETION,
            description="从数据库查询并补全字段",
            template="db.{table}.{field}[{conditions}]",
            example="db.companies.tax_number[name=invoice.supplier.name]"
        ),
        RulePattern(
            name="计算补全",
            type=RuleType.COMPLETION,
            description="基于其他字段计算补全",
            template="{field1} * {field2}",
            example="invoice.total_amount * 0.06"
        ),
        RulePattern(
            name="条件补全",
            type=RuleType.COMPLETION,
            description="基于条件的补全",
            template="condition ? value1 : value2",
            example="invoice.total_amount > 5000 ? 'LARGE' : 'NORMAL'"
        ),
        RulePattern(
            name="默认值补全",
            type=RuleType.COMPLETION,
            description="设置默认值",
            template="'default_value'",
            example="'CN'"
        ),
        RulePattern(
            name="API函数补全",
            type=RuleType.COMPLETION,
            description="使用产品API函数",
            template="get_function_name(parameter)",
            example="get_standard_name(item.description)"
        ),
    ]


def get_validation_patterns() -> List[RulePattern]:
    """Get common validation rule patterns"""
    return [
        RulePattern(
            name="必填校验",
            type=RuleType.VALIDATION,
            description="检查字段是否存在且非空",
            template="has({field}) && {field} != ''",
            example="has(invoice.invoice_number) && invoice.invoice_number != ''"
        ),
        RulePattern(
            name="格式校验",
            type=RuleType.VALIDATION,
            description="正则表达式格式校验",
            template="{field}.matches('{pattern}')",
            example="invoice.supplier.tax_no.matches('^[0-9]{15}[A-Z0-9]{3}$')"
        ),
        RulePattern(
            name="范围校验",
            type=RuleType.VALIDATION,
            description="数值范围校验",
            template="{field} >= {min} && {field} <= {max}",
            example="invoice.tax_amount >= invoice.total_amount * 0.05 && invoice.tax_amount <= invoice.total_amount * 0.13"
        ),
        RulePattern(
            name="列表校验",
            type=RuleType.VALIDATION,
            description="列表元素校验",
            template="{list}.all(item, {condition})",
            example="invoice.items.all(item, item.amount > 0 && item.quantity > 0)"
        ),
        RulePattern(
            name="数据库存在性校验",
            type=RuleType.VALIDATION,
            description="验证数据在数据库中存在",
            template="db.{table}.{field}[{conditions}] != null",
            example="db.companies.tax_number[name=invoice.supplier.name] != null"
        ),
    ]