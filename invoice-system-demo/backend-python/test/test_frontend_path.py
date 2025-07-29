#!/usr/bin/env python3
"""测试前端调用路径的CEL字段补全功能"""

import asyncio
import sys
import os
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from app.services.invoice_service import InvoiceProcessingService
from app.database.connection import init_database, get_db

async def test_frontend_path():
    """测试前端调用路径"""
    print("=== 测试前端调用路径的CEL字段补全 ===")
    
    # 读取测试XML文件
    xml_file = project_root / "data" / "invoice1.xml"
    if not xml_file.exists():
        print(f"错误: 测试文件不存在 {xml_file}")
        return
    
    with open(xml_file, 'r', encoding='utf-8') as f:
        kdubl_xml = f.read()
    
    print(f"读取测试文件: {xml_file}")
    
    # 初始化数据库
    await init_database()
    
    # 获取数据库会话
    async for db_session in get_db():
        try:
            # 创建带数据库会话的发票服务（模拟前端调用）
            invoice_service = InvoiceProcessingService(db_session)
            
            print("开始处理发票...")
            result = await invoice_service.process_kdubl_invoice(kdubl_xml, "ERP")
            
            print("\n=== 处理结果 ===")
            print(f"成功: {result['success']}")
            print(f"错误: {result['errors']}")
            
            print("\n=== 处理步骤 ===")
            for step in result['steps']:
                print(f"  {step}")
            
            print("\n=== 字段补全日志 ===")
            for log in result['execution_details']['completion_logs']:
                print(f"  {log['message']}")
            
            if result['success'] and result['data']:
                domain_data = result['data']['domain_object']
                print(f"\n=== 发票信息 ===")
                print(f"发票号: {domain_data.get('invoice_number')}")
                print(f"发票类型: {domain_data.get('invoice_type')}")
                print(f"国家: {domain_data.get('country')}")
                
                print(f"\n=== 商品信息 ===")
                items = domain_data.get('items', [])
                for i, item in enumerate(items):
                    print(f"商品 {i+1}:")
                    print(f"  描述: {item.get('description')}")
                    print(f"  税率: {item.get('tax_rate')}")
                    print(f"  税额: {item.get('tax_amount')}")
                    print(f"  净额: {item.get('net_amount')}")
            
            break
        except Exception as e:
            print(f"处理过程中发生错误: {str(e)}")
            import traceback
            traceback.print_exc()
            break

if __name__ == "__main__":
    asyncio.run(test_frontend_path())