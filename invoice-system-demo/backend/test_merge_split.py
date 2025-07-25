#!/usr/bin/env python3
"""
发票合并拆分功能测试脚本
使用 invoice1.xml 和 invoice2.xml 测试发票合并拆分功能
直接调用现有的发票处理接口，自动完成补全和合并拆分
"""

import sys
import os
import asyncio
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from app.services.invoice_service import InvoiceProcessingService


async def test_invoice_merge_split_with_processing_service():
    """使用发票处理服务测试合并拆分功能"""
    print("="*60)
    print("发票合并拆分功能测试")
    print("使用 InvoiceProcessingService 进行完整的处理流程")
    print("="*60)
    
    # 读取测试XML文件
    invoice1_path = project_root / "data" / "invoice1.xml"
    invoice2_path = project_root / "data" / "invoice2.xml"
    
    with open(invoice1_path, 'r', encoding='utf-8') as f:
        invoice1_xml = f.read()
    
    with open(invoice2_path, 'r', encoding='utf-8') as f:
        invoice2_xml = f.read()
    
    # 创建发票处理服务（不使用数据库）
    service = InvoiceProcessingService(db_session=None)
    
    # 调用统一处理接口
    print("\n正在处理发票...")
    result = await service.process_invoices(
        inputs=[invoice1_xml, invoice2_xml],
        source_system="ERP",
        merge_strategy="by_tax_party",
        merge_config=None
    )
    
    # 打印处理结果
    print(f"\n处理结果:")
    print(f"  成功: {result['success']}")
    print(f"  批次ID: {result['batch_id']}")
    print(f"  输入发票数量: {result['total_inputs']}")
    print(f"  处理时间: {result['processing_time']}")
    
    print(f"\n摘要信息:")
    summary = result['summary']
    print(f"  总输入: {summary['total_inputs']}")
    print(f"  成功处理: {summary['successful_inputs']}")
    print(f"  处理失败: {summary['failed_inputs']}")
    print(f"  最终输出发票数量: {summary['total_output_invoices']}")
    
    # 打印补全日志
    if result.get('execution_details', {}).get('completion_logs'):
        print(f"\n字段补全详情:")
        for log in result['execution_details']['completion_logs']:
            print(f"  ✅ {log.get('rule_name', 'Unknown')}: {log.get('target_field', '')} = {log.get('value', '')}")
    
    # 打印验证日志
    if result.get('execution_details', {}).get('validation_logs'):
        print(f"\n验证详情:")
        for log in result['execution_details']['validation_logs']:
            status = "✅" if log.get('status') == 'passed' else "❌" if log.get('status') == 'failed' else "⚠️"
            print(f"  {status} {log.get('rule_name', 'Unknown')}: {log.get('message', log.get('error_message', ''))}")
    
    # 打印最终发票详情
    if result.get('results'):
        print(f"\n最终发票详情:")
        for i, invoice_result in enumerate(result['results'], 1):
            invoice = invoice_result.get('invoice')
            if invoice:
                print(f"\n  发票 {i}:")
                print(f"    发票号: {invoice.invoice_number}")
                print(f"    购方税号: {invoice.customer.tax_no}")
                print(f"    销方税号: {invoice.supplier.tax_no}")
                print(f"    总金额: {invoice.total_amount}")
                print(f"    明细行数量: {len(invoice.items)}")
                
                print(f"    明细行:")
                for j, item in enumerate(invoice.items, 1):
                    print(f"      {j}. {item.description} - 数量:{item.quantity} 金额:{item.amount} 税种:{getattr(item, 'tax_category', 'N/A')}")
    
    # 检查是否符合预期
    final_count = summary['total_output_invoices']
    if final_count == 3:
        print(f"\n✅ 测试成功！最终形成了{final_count}张发票，符合预期。")
    else:
        print(f"\n⚠️  测试结果与预期不符。预期3张发票，实际{final_count}张发票。")
    
    return result


def print_detailed_analysis(result):
    """打印详细分析"""
    print("\n" + "="*60)
    print("详细分析")
    print("="*60)
    
    # 分析文件处理情况
    if result.get('file_mapping'):
        print("\n文件处理情况:")
        for file_info in result['file_mapping']:
            status = "✅ 成功" if file_info.get('success') else "❌ 失败"
            print(f"  {file_info.get('filename', 'Unknown')}: {status}")
            if file_info.get('error'):
                print(f"    错误: {file_info['error']}")
            if file_info.get('invoice_number'):
                print(f"    发票号: {file_info['invoice_number']}")
    
    # 分析补全情况
    if result.get('execution_details', {}).get('completion_by_file'):
        print("\n按文件分组的补全情况:")
        for file_completion in result['execution_details']['completion_by_file']:
            print(f"  文件 {file_completion.get('file_name', 'Unknown')}:")
            print(f"    发票号: {file_completion.get('invoice_number', 'Unknown')}")
            print(f"    补全规则数量: {len(file_completion.get('completion_logs', []))}")
            for log in file_completion.get('completion_logs', []):
                print(f"      - {log.get('rule_name', 'Unknown')}: {log.get('target_field', '')} = {log.get('value', '')}")


if __name__ == "__main__":
    try:
        # 运行异步测试
        result = asyncio.run(test_invoice_merge_split_with_processing_service())
        
        # 打印详细分析
        print_detailed_analysis(result)
        
    except Exception as e:
        print(f"\n❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()