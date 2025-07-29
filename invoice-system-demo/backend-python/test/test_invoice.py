"""发票处理测试脚本"""
import os
import sys
from pathlib import Path

# 添加项目路径
sys.path.append(str(Path(__file__).parent.parent))

from app.services.invoice_service import InvoiceProcessingService
from app.services.channel_service import MockChannelService


def test_invoice_processing():
    """测试发票处理流程"""
    print("=== 发票处理系统测试 ===\n")
    
    # 初始化服务
    invoice_service = InvoiceProcessingService()
    channel_service = MockChannelService()
    
    # 读取测试数据
    data_path = Path(__file__).parent / "data" / "invoice1.xml"
    if not data_path.exists():
        print(f"测试文件不存在: {data_path}")
        return
    
    with open(data_path, 'r', encoding='utf-8') as f:
        kdubl_xml = f.read()
    
    print(f"1. 读取测试文件: {data_path.name}")
    print("-" * 50)
    
    # 处理发票
    print("\n2. 执行发票处理")
    print("-" * 50)
    result = invoice_service.process_kdubl_invoice(kdubl_xml, "ERP")
    
    # 打印处理步骤
    print("\n处理步骤:")
    for step in result['steps']:
        print(f"  {step}")
    
    # 打印结果
    if result['success']:
        print("\n✓ 处理成功!")
        domain = result['data']['domain_object']
        print(f"\n发票信息:")
        print(f"  - 发票号: {domain['invoice_number']}")
        print(f"  - 开票日期: {domain['issue_date']}")
        print(f"  - 供应商: {domain['supplier']['name']}")
        print(f"  - 客户: {domain['customer']['name']}")
        print(f"  - 总金额: {domain['total_amount']}")
        print(f"  - 税额: {domain.get('tax_amount', '未计算')}")
        print(f"  - 净额: {domain.get('net_amount', '未计算')}")
        
        # 测试合规性校验
        print("\n3. 执行合规性校验（模拟）")
        print("-" * 50)
        compliance_result = channel_service.validate_compliance(
            result['data']['processed_kdubl'], 
            "CN"
        )
        print(f"  合规状态: {compliance_result['compliance_status']}")
        if compliance_result['warnings']:
            print(f"  警告: {', '.join(compliance_result['warnings'])}")
            
    else:
        print("\n✗ 处理失败!")
        print(f"错误: {result['errors']}")
    
    # 显示已加载的规则
    print("\n4. 已加载的规则")
    print("-" * 50)
    rules = invoice_service.get_loaded_rules()
    print(f"  补全规则: {len(rules['completion_rules'])}条")
    print(f"  校验规则: {len(rules['validation_rules'])}条")


def test_rule_configuration():
    """测试规则配置"""
    print("\n\n=== 规则配置测试 ===\n")
    
    invoice_service = InvoiceProcessingService()
    rules = invoice_service.get_loaded_rules()
    
    print("补全规则:")
    for rule in rules['completion_rules']:
        print(f"  - {rule['name']} (优先级: {rule['priority']}, 状态: {'启用' if rule['active'] else '禁用'})")
    
    print("\n校验规则:")
    for rule in rules['validation_rules']:
        print(f"  - {rule['name']} (优先级: {rule['priority']}, 状态: {'启用' if rule['active'] else '禁用'})")


if __name__ == "__main__":
    test_invoice_processing()
    test_rule_configuration()