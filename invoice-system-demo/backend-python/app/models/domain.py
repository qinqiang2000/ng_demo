"""Domain Object定义 - 仅用于内存中的业务处理"""
from typing import Dict, List, Optional, Any
from datetime import date
from decimal import Decimal
from pydantic import BaseModel, Field


class Address(BaseModel):
    """地址信息"""
    street: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    postal_code: Optional[str] = None
    country: Optional[str] = None


class Party(BaseModel):
    """参与方信息（供应商/客户）"""
    name: str
    tax_no: Optional[str] = None
    address: Optional[Address] = None
    email: Optional[str] = None
    phone: Optional[str] = None
    bank_account: Optional[str] = None
    extra: Dict[str, Any] = Field(default_factory=dict)


class InvoiceItem(BaseModel):
    """发票项目明细"""
    item_id: str
    description: str
    name: Optional[str] = None  # 标准商品名称（用于开票）
    product_code: Optional[str] = None
    quantity: Decimal
    unit: str = "EA"
    unit_price: Decimal
    amount: Decimal
    tax_rate: Optional[Decimal] = None
    tax_amount: Optional[Decimal] = None
    tax_category: Optional[str] = None
    note: Optional[str] = None
    extra: Dict[str, Any] = Field(default_factory=dict)


class InvoiceDomainObject(BaseModel):
    """发票Domain Object - 业务层的内存模型"""
    # 基础信息
    invoice_number: str
    issue_date: date
    invoice_type: str
    country: str = "CN"
    tenant_id: Optional[str] = None
    
    # 供应商信息
    supplier: Party
    customer: Party
    
    # 商品明细
    items: List[InvoiceItem]
    
    # 金额信息
    total_amount: Decimal
    tax_amount: Optional[Decimal] = None
    net_amount: Optional[Decimal] = None
    
    # 扩展字段
    extensions: Dict[str, Any] = Field(default_factory=dict)
    
    class Config:
        arbitrary_types_allowed = True