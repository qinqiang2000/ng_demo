#!/usr/bin/env python3
"""
测试CEL字段补全功能
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app.models.domain import InvoiceDomainObject, Party, InvoiceItem
from app.core.cel_engine import CELFieldCompletionEngine
from decimal import Decimal
from datetime import date

def test_cel_completion():
    print("=== CEL字段补全测试 ===")
    
    # 创建测试发票对象
    invoice = InvoiceDomainObject(
        invoice_number="INV-2024-001",
        issue_date=date.today(),
        invoice_type="STANDARD",
        supplier=Party(name="测试供应商", tax_no="123456789012345ABC"),
        customer=Party(name="测试客户"),
        total_amount=Decimal("1000.00"),
        items=[
            InvoiceItem(
                item_id="item1",
                description="办公用品",
                quantity=Decimal("10"),
                unit_price=Decimal("50.00"),
                amount=Decimal("500.00"),
                tax_rate=None  # 需要补全
            ),
            InvoiceItem(
                item_id="item2",
                description="电子设备",
                quantity=Decimal("2"),
                unit_price=Decimal("250.00"),
                amount=Decimal("500.00"),
                tax_rate=None  # 需要补全
            )
        ]
    )
    
    print(f"补全前 - 第一个商品税率: {invoice.items[0].tax_rate}")
    print(f"补全前 - 第二个商品税率: {invoice.items[1].tax_rate}")
    
    # 创建CEL补全引擎
    engine = CELFieldCompletionEngine()
    
    # 执行字段补全
    result = engine.complete(invoice)
    
    print(f"补全后 - 第一个商品税率: {invoice.items[0].tax_rate}")
    print(f"补全后 - 第二个商品税率: {invoice.items[1].tax_rate}")
    print(f"补全后 - 第一个商品税额: {getattr(invoice.items[0], 'tax_amount', None)}")
    print(f"补全后 - 第二个商品税额: {getattr(invoice.items[1], 'tax_amount', None)}")
    
    print(f"补全结果: {result}")

if __name__ == "__main__":
    test_cel_completion()