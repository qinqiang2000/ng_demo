#!/usr/bin/env python3
"""
测试校验失败时是否返回完整数据
"""

import requests
import json

def test_validation_failure_with_data():
    """测试校验失败时是否返回完整的发票数据"""
    
    # 测试数据 - 包含不符合旅游服务规范的发票项目
    test_kdubl = """<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="http://www.kdubl.org/schema/invoice/1.0">
    <InvoiceHeader>
        <InvoiceNumber>TEST_001</InvoiceNumber>
        <InvoiceDate>2024-01-15</InvoiceDate>
        <InvoiceType>增值税专用发票</InvoiceType>
        <TotalAmount>1000.00</TotalAmount>
        <TaxAmount>130.00</TaxAmount>
        <NetAmount>870.00</NetAmount>
    </InvoiceHeader>
    <Parties>
        <Supplier>
            <Name>测试旅游公司</Name>
            <TaxNo>91110000123456789X</TaxNo>
        </Supplier>
        <Customer>
            <Name>测试客户公司</Name>
            <TaxNo>91110000987654321Y</TaxNo>
        </Customer>
    </Parties>
    <InvoiceItems>
        <Item>
            <ItemId>1</ItemId>
            <Description>旅游服务费</Description>
            <Quantity>1</Quantity>
            <Unit>次</Unit>
            <UnitPrice>870.00</UnitPrice>
            <Amount>870.00</Amount>
            <TaxAmount>130.00</TaxAmount>
        </Item>
    </InvoiceItems>
</Invoice>"""

    # 发送请求
    url = "http://localhost:8000/api/invoice/process"
    payload = {
        "kdubl_xml": test_kdubl,
        "source_system": "ERP",
        "merge_strategy": "by_tax_party"
    }
    
    try:
        response = requests.post(url, json=payload)
        response.raise_for_status()
        
        result = response.json()
        
        print("=== 测试结果 ===")
        print(f"整体成功状态: {result.get('success', 'N/A')}")
        print(f"处理时间: {result.get('processing_time', 'N/A')}")
        print()
        
        # 检查结果
        if "results" in result and len(result["results"]) > 0:
            for i, invoice_result in enumerate(result["results"]):
                print(f"--- 发票 {i+1} ---")
                print(f"发票号: {invoice_result.get('invoice_number', 'N/A')}")
                print(f"校验成功: {invoice_result.get('success', 'N/A')}")
                
                # 检查是否有错误信息
                if "errors" in invoice_result:
                    print("校验错误:")
                    for error in invoice_result["errors"]:
                        print(f"  - {error}")
                
                # 关键检查：即使校验失败，是否仍然返回了数据
                if "data" in invoice_result:
                    print("✅ 数据已返回 (即使校验失败)")
                    domain_obj = invoice_result["data"].get("domain_object", {})
                    print(f"  发票号: {domain_obj.get('invoice_number', 'N/A')}")
                    print(f"  总金额: {domain_obj.get('total_amount', 'N/A')}")
                    print(f"  明细项数量: {len(domain_obj.get('items', []))}")
                    
                    if domain_obj.get('items'):
                        print("  明细项:")
                        for item in domain_obj['items']:
                            print(f"    - {item.get('description', 'N/A')}: ¥{item.get('amount', 0)}")
                else:
                    print("❌ 未返回数据")
                
                print()
        
        # 检查校验日志
        if "execution_details" in result and "validation_logs" in result["execution_details"]:
            print("=== 校验执行日志 ===")
            for log in result["execution_details"]["validation_logs"]:
                status_icon = "✅" if log.get("status") == "passed" else "❌"
                print(f"{status_icon} {log.get('rule_name', 'N/A')}: {log.get('status', 'N/A')}")
                if log.get("message"):
                    print(f"   消息: {log['message']}")
        
        return result
        
    except requests.exceptions.RequestException as e:
        print(f"请求失败: {e}")
        return None
    except json.JSONDecodeError as e:
        print(f"JSON解析失败: {e}")
        return None

if __name__ == "__main__":
    print("测试校验失败时是否返回完整数据...")
    print("=" * 50)
    test_validation_failure_with_data()