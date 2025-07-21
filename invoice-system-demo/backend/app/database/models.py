"""
数据库模型定义
"""
from sqlalchemy import Column, Integer, String, Float, Boolean, Text, DateTime, Index
from sqlalchemy.sql import func
from app.database.connection import Base


class Company(Base):
    """企业信息表"""
    __tablename__ = "companies"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(200), nullable=False, comment="企业名称")
    tax_number = Column(String(20), unique=True, index=True, comment="税号")
    address = Column(Text, comment="企业地址")
    phone = Column(String(50), comment="联系电话")
    email = Column(String(100), comment="邮箱")
    category = Column(String(50), comment="企业分类", default="GENERAL")
    is_active = Column(Boolean, default=True, comment="是否启用")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    # 创建索引
    __table_args__ = (
        Index('idx_company_name', 'name'),
        Index('idx_company_category', 'category'),
    )


class TaxRate(Base):
    """税率配置表"""
    __tablename__ = "tax_rates"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(100), nullable=False, comment="税率名称")
    rate = Column(Float, nullable=False, comment="税率值")
    category = Column(String(50), comment="适用类别")
    min_amount = Column(Float, comment="最小适用金额", default=0)
    max_amount = Column(Float, comment="最大适用金额")
    description = Column(Text, comment="税率说明")
    is_active = Column(Boolean, default=True, comment="是否启用")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    # 创建索引
    __table_args__ = (
        Index('idx_tax_rate_category', 'category'),
        Index('idx_tax_rate_amount', 'min_amount', 'max_amount'),
    )


class BusinessRule(Base):
    """业务规则配置表（可选，用于动态规则管理）"""
    __tablename__ = "business_rules"
    
    id = Column(Integer, primary_key=True, index=True)
    rule_id = Column(String(50), unique=True, nullable=False, comment="规则ID")
    rule_name = Column(String(200), nullable=False, comment="规则名称")
    rule_type = Column(String(20), nullable=False, comment="规则类型: completion/validation")
    apply_to = Column(Text, comment="应用条件")
    target_field = Column(String(100), comment="目标字段")
    field_path = Column(String(100), comment="校验字段路径")
    rule_expression = Column(Text, nullable=False, comment="规则表达式")
    error_message = Column(Text, comment="错误消息")
    priority = Column(Integer, default=50, comment="优先级")
    is_active = Column(Boolean, default=True, comment="是否启用")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    # 创建索引
    __table_args__ = (
        Index('idx_rule_type', 'rule_type'),
        Index('idx_rule_priority', 'priority'),
    )