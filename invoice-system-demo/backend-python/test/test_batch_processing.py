"""批量处理功能测试"""
import asyncio
import sys
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from app.services.invoice_service import InvoiceProcessingService
from app.core.invoice_merge_engine import MergeStrategy
from app.database.connection import init_database, get_db
from fastapi import UploadFile
from io import BytesIO


class MockUploadFile:
    """模拟UploadFile对象"""
    
    def __init__(self, filename: str, content: str):
        self.filename = filename
        self.content = content.encode('utf-8')
        self.file = BytesIO(self.content)
        
    async def read(self):
        return self.content
        
    async def seek(self, position):
        self.file.seek(position)


async def test_batch_processing():
    """测试批量处理功能"""
    print("=== 测试统一发票处理功能（批量模式）===\n")
    
    # 初始化数据库
    await init_database()
    
    # 使用真实的XML文件进行测试
    xml_files = [
        "/Users/qinqiang02/workspace/ml/ng_demo/invoice-system-demo/backend/data/invoice1.xml",
        "/Users/qinqiang02/workspace/ml/ng_demo/invoice-system-demo/backend/data/invoice2.xml"
    ]
    
    # 创建测试文件
    mock_files = []
    for xml_file in xml_files:
        with open(xml_file, 'r', encoding='utf-8') as f:
            xml_content = f.read()
        filename = Path(xml_file).name
        mock_file = MockUploadFile(filename, xml_content)
        mock_files.append(mock_file)
    
    print(f"准备了 {len(mock_files)} 个测试文件")
    
    # 获取数据库会话
    async for db_session in get_db():
        try:
            # 创建统一处理服务
            invoice_service = InvoiceProcessingService(db_session)
            
            print("\n=== 测试1: 无合并策略批量处理 ===")
            result1 = await invoice_service.process_invoices(
                inputs=mock_files,
                source_system="ERP",
                merge_strategy="none"
            )
            
            print(f"批次ID: {result1['batch_id']}")
            print(f"处理时间: {result1['processing_time']}")
            print(f"总体成功: {result1['success']}")
            print(f"总输入数: {result1['total_inputs']}")
            
            print("\n--- 处理摘要 ---")
            summary = result1['summary']
            print(f"成功输入: {summary['successful_inputs']}, 失败输入: {summary['failed_inputs']}")
            print(f"输出发票数: {summary['total_output_invoices']}")
            
            print("\n--- 文件映射状态 ---")
            for mapping in result1['file_mapping']:
                status = "✓" if mapping['success'] else "✗"
                print(f"{status} {mapping['filename']}: {mapping.get('invoice_number', mapping.get('error', 'unknown'))}")
            
            print("\n--- 最终结果 ---")
            for result in result1['results']:
                print(f"发票 {result['invoice_id']}: {'✓' if result['success'] else '✗'}")
                if result['success']:
                    print(f"  发票号: {result['invoice_number']}")
                else:
                    print(f"  错误: {result.get('errors', [])}")
            
            print("\n=== 测试2: 按客户合并策略 ===")
            result2 = await invoice_service.process_invoices(
                inputs=mock_files,
                source_system="ERP",
                merge_strategy="by_customer"
            )
            
            print(f"批次ID: {result2['batch_id']}")
            print(f"总输入数: {result2['total_inputs']}")
            print(f"输出发票数: {result2['summary']['total_output_invoices']}")
            
            print("\n--- 执行日志 ---")
            if result2['execution_details']['completion_logs']:
                print("补全日志:")
                for log in result2['execution_details']['completion_logs']:
                    print(f"  {log}")
            
            if result2['execution_details']['validation_logs']:
                print("校验日志:")
                for log in result2['execution_details']['validation_logs']:
                    print(f"  {log}")
            
            print("\n=== 测试3: 单张发票处理（作为批量的特例）===")
            # 测试单张发票
            with open(xml_files[0], 'r', encoding='utf-8') as f:
                single_xml = f.read()
            
            result3 = await invoice_service.process_invoices(
                inputs=single_xml,
                source_system="ERP",
                merge_strategy="none"
            )
            
            print(f"批次ID: {result3['batch_id']}")
            print(f"总输入数: {result3['total_inputs']}")
            print(f"输出发票数: {result3['summary']['total_output_invoices']}")
            print(f"处理成功: {result3['success']}")
            
            print("\n=== 测试完成 ===")
            
        except Exception as e:
            print(f"测试异常: {str(e)}")
            import traceback
            traceback.print_exc()
        finally:
            break


if __name__ == "__main__":
    asyncio.run(test_batch_processing())