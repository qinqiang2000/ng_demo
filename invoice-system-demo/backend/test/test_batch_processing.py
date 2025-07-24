"""批量处理功能测试"""
import asyncio
import sys
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from app.services.batch_invoice_service import BatchInvoiceProcessingService
from app.services.invoice_merge_service import MergeStrategy
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
    print("=== 测试批量发票处理功能 ===\n")
    
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
            # 创建批量处理服务
            batch_service = BatchInvoiceProcessingService(db_session)
            
            print("\n=== 测试1: 无合并策略批量处理 ===")
            result1 = await batch_service.process_batch_invoices(
                xml_files=mock_files,
                source_system="ERP",
                merge_strategy="none"
            )
            
            print(f"批次ID: {result1['batch_id']}")
            print(f"处理时间: {result1['processing_time']}")
            print(f"总体成功: {result1['success']}")
            print(f"总文件数: {result1['total_files']}")
            
            print("\n--- 补全阶段结果 ---")
            completion = result1['stages']['completion']
            print(f"成功: {completion['successful_count']}, 失败: {completion['failed_count']}")
            
            print("\n--- 合并阶段结果 ---")
            merge = result1['stages']['merge']
            print(f"输入发票数: {merge['input_invoices']}, 输出发票数: {merge['output_invoices']}")
            print(f"合并策略: {merge['merge_strategy']}")
            
            print("\n--- 校验阶段结果 ---")
            validation = result1['stages']['validation']
            print(f"通过: {validation['passed_count']}, 失败: {validation['failed_count']}")
            
            print("\n--- 最终结果 ---")
            for result in result1['results']:
                print(f"发票 {result['invoice_id']}: {result['success']}")
                if result['success']:
                    print(f"  发票号: {result['invoice_number']}")
                else:
                    print(f"  错误: {result.get('errors', [])}")
            
            print("\n=== 测试2: 按客户合并策略 ===")
            result2 = await batch_service.process_batch_invoices(
                xml_files=mock_files,
                source_system="ERP",
                merge_strategy="by_customer"
            )
            
            print(f"批次ID: {result2['batch_id']}")
            print(f"合并策略: {result2['stages']['merge']['merge_strategy']}")
            print(f"合并前: {result2['stages']['merge']['input_invoices']} 张发票")
            print(f"合并后: {result2['stages']['merge']['output_invoices']} 张发票")
            
            print("\n--- 合并执行日志 ---")
            for log in result2['stages']['merge']['execution_log']:
                print(f"  {log}")
            
            print("\n=== 测试完成 ===")
            
        except Exception as e:
            print(f"测试异常: {str(e)}")
            import traceback
            traceback.print_exc()
        finally:
            break


if __name__ == "__main__":
    asyncio.run(test_batch_processing())