"""规则配置模型"""
from typing import Optional, Any, Dict
from pydantic import BaseModel, Field
from datetime import datetime


class BaseRule(BaseModel):
    """规则基类"""
    id: str
    tenant_id: Optional[str] = None
    rule_name: str
    apply_to: str  # 应用条件
    priority: int = 100
    active: bool = True
    created_at: datetime = Field(default_factory=datetime.now)
    

class FieldCompletionRule(BaseRule):
    """字段补全规则"""
    target_field: str  # 目标字段路径(Domain Object)
    rule_expression: str  # 表达式，返回字段值
    rule_type: str = "DEFAULT"  # DEFAULT: 仅在字段为空时设置, OVERRIDE: 总是覆盖
    

class FieldValidationRule(BaseRule):
    """字段校验规则"""
    field_path: str  # 要校验的字段路径(Domain Object)
    rule_expression: str  # 表达式，返回true/false
    error_message: str  # 校验失败的错误信息


class ComplianceTemplate(BaseModel):
    """合规模板"""
    id: str
    country: str
    invoice_type: str
    version: str
    schema_definition: Optional[str] = None  # XSD验证规则
    tax_rules: Dict[str, Any] = Field(default_factory=dict)
    active: bool = True
    effective_date: datetime
    created_at: datetime = Field(default_factory=datetime.now)