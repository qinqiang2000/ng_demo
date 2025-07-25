#!/usr/bin/env python3
"""测试发票合并拆分逻辑"""

import sys
import os
from datetime import date
from decimal import Decimal

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.models.domain import InvoiceDomainObject, Party, InvoiceItem
from app.core.invoice_merge_engine import InvoiceMergeEngine, MergeStrategy


def create_test_invoice(invoice_number: str, customer_tax_no: str, supplier_tax_no: str, items: list) -> InvoiceDomainObject:
    """创建测试发票"""
    customer = Party(name="测试客户", tax_no=customer_tax_no)
    supplier = Party(name="测试供应商", tax_no=supplier_tax_no)
    
    total_amount = sum(item.amount for item in items)
    total_tax_amount = sum(item.tax_amount or Decimal('0') for item in items)
    
    return InvoiceDomainObject(
        invoice_number=invoice_number,
        issue_date=date.today(),
        invoice_type="普通发票",
        supplier=supplier,
        customer=customer,
        items=items,
        total_amount=total_amount,
        tax_amount=total_tax_amount,
        net_amount=total_amount - total_tax_amount
    )


def create_test_item(item_id: str, name: str, tax_rate: Decimal, tax_category: str, 
                    quantity: Decimal, unit_price: Decimal) -> InvoiceItem:
    """创建测试发票明细"""
    amount = quantity * unit_price
    tax_amount = amount * tax_rate
    
    return InvoiceItem(
        item_id=item_id,
        description=f"商品描述-{name}",
        name=name,
        quantity=quantity,
        unit="个",
        unit_price=unit_price,
        amount=amount,
        tax_rate=tax_rate,
        tax_amount=tax_amount,
        tax_category=tax_category
    )


def test_merge_and_split():
    """测试合并拆分逻辑"""
    print("=== 测试发票合并拆分逻辑 ===\n")
    
    # 创建测试数据
    # 发票1：客户A，供应商X，包含两种税种的商品
    items1 = [
        create_test_item("1", "商品A", Decimal('0.13'), "VAT", Decimal('10'), Decimal('100')),
        create_test_item("2", "商品B", Decimal('0.06'), "SIMPLE", Decimal('5'), Decimal('200')),
    ]
    invoice1 = create_test_invoice("INV001", "CUSTOMER_A", "SUPPLIER_X", items1)
    
    # 发票2：客户A，供应商X（相同税号），包含相同商品A和新商品C
    items2 = [
        create_test_item("3", "商品A", Decimal('0.13'), "VAT", Decimal('5'), Decimal('100')),  # 与发票1的商品A相同
        create_test_item("4", "商品C", Decimal('0.13'), "VAT", Decimal('3'), Decimal('150')),
    ]
    invoice2 = create_test_invoice("INV002", "CUSTOMER_A", "SUPPLIER_X", items2)
    
    # 发票3：客户B，供应商X（不同客户）
    items3 = [
        create_test_item("5", "商品D", Decimal('0.06'), "SIMPLE", Decimal('8'), Decimal('80')),
    ]
    invoice3 = create_test_invoice("INV003", "CUSTOMER_B", "SUPPLIER_X", items3)
    
    invoices = [invoice1, invoice2, invoice3]
    
    print(f"原始发票数量: {len(invoices)}")
    for i, inv in enumerate(invoices, 1):
        print(f"发票{i}: {inv.invoice_number}, 客户税号: {inv.customer.tax_no}, 供应商税号: {inv.supplier.tax_no}")
        print(f"  明细数量: {len(inv.items)}, 总金额: {inv.total_amount}")
        for item in inv.items:
            print(f"    - {item.name}: 数量{item.quantity}, 单价{item.unit_price}, 税率{item.tax_rate}, 税种{item.tax_category}")
    
    print("\n=== 执行合并拆分 ===")
    
    # 创建合并引擎
    engine = InvoiceMergeEngine()
    
    # 执行合并拆分
    result_invoices = engine.merge_and_split(
        invoices, 
        MergeStrategy.BY_TAX_PARTY,  # 使用新的按税号合并策略
        merge_config=None,
        split_config=None
    )
    
    print(f"\n处理后发票数量: {len(result_invoices)}")
    for i, inv in enumerate(result_invoices, 1):
        print(f"发票{i}: {inv.invoice_number}, 客户税号: {inv.customer.tax_no}, 供应商税号: {inv.supplier.tax_no}")
        print(f"  明细数量: {len(inv.items)}, 总金额: {inv.total_amount}")
        for item in inv.items:
            print(f"    - {item.name}: 数量{item.quantity}, 单价{item.unit_price}, 税率{item.tax_rate}, 税种{item.tax_category}")
    
    print("\n=== 执行日志 ===")
    for log in engine.get_execution_log():
        print(f"- {log}")
    
    print("\n=== 验证结果 ===")
    
    # 验证合并逻辑
    # 应该有3张发票：
    # 1. 客户A+供应商X的VAT税种发票（合并了商品A的数量）
    # 2. 客户A+供应商X的SIMPLE税种发票
    # 3. 客户B+供应商X的SIMPLE税种发票
    
    expected_count = 3  # 预期3张发票
    if len(result_invoices) == expected_count:
        print(f"✓ 发票数量正确: {len(result_invoices)}")
    else:
        print(f"✗ 发票数量错误: 期望{expected_count}, 实际{len(result_invoices)}")
    
    # 检查是否正确合并了相同的商品A
    vat_invoices = [inv for inv in result_invoices if any(item.tax_category == "VAT" for item in inv.items)]
    if vat_invoices:
        vat_invoice = vat_invoices[0]
        product_a_items = [item for item in vat_invoice.items if item.name == "商品A"]
        if product_a_items:
            merged_quantity = product_a_items[0].quantity
            expected_quantity = Decimal('15')  # 10 + 5
            if merged_quantity == expected_quantity:
                print(f"✓ 商品A数量合并正确: {merged_quantity}")
            else:
                print(f"✗ 商品A数量合并错误: 期望{expected_quantity}, 实际{merged_quantity}")
    
    print("\n测试完成！")


if __name__ == "__main__":
    test_merge_and_split()