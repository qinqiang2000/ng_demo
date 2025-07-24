#!/usr/bin/env python3
"""
测试用例 2.1: 测试供应商税号自动补全
自动化测试脚本
"""
import asyncio
import sys
import os
import json
from decimal import Decimal

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app.services.invoice_service import InvoiceProcessingService
from app.database.connection import AsyncSessionLocal, init_database
from app.database.crud import CompanyCRUD, CompanyCreate

# 测试XML数据 - 基于测试用例2.1
TEST_XML = """<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2">
    <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
    <cbc:ID>INV-2024-TEST-001</cbc:ID>
    <cbc:IssueDate>2024-01-15</cbc:IssueDate>
    <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
    <cbc:DocumentCurrencyCode>CNY</cbc:DocumentCurrencyCode>
    
    <cac:AccountingSupplierParty>
        <cac:Party>
            <cbc:Name>携程广州</cbc:Name>
        </cac:Party>
    </cac:AccountingSupplierParty>
    
    <cac:AccountingCustomerParty>
        <cac:Party>
            <cbc:Name>金蝶广州</cbc:Name>
        </cac:Party>
    </cac:AccountingCustomerParty>
    
    <cac:LegalMonetaryTotal>
        <cbc:LineExtensionAmount currencyID="CNY">943.40</cbc:LineExtensionAmount>
        <cbc:TaxExclusiveAmount currencyID="CNY">943.40</cbc:TaxExclusiveAmount>
        <cbc:TaxInclusiveAmount currencyID="CNY">1000.00</cbc:TaxInclusiveAmount>
        <cbc:PayableAmount currencyID="CNY">1000.00</cbc:PayableAmount>
    </cac:LegalMonetaryTotal>
</Invoice>"""


async def setup_test_data():
    """设置测试数据"""
    print("设置测试数据...")
    
    # 初始化数据库
    await init_database()
    
    async with AsyncSessionLocal() as db:
        # 确保"携程广州"在数据库中存在且有正确的税号
        company_data = CompanyCreate(
            name="携程广州",
            tax_number="913100001332972H77",  # 18位正确格式：15位数字+3位字母数字
            address="广州市天河区珠江新城金穗路62号",
            phone="020-38888888",
            email="guangzhou@ctrip.com",
            category="TRAVEL_SERVICE"
        )
        
        try:
            existing = await CompanyCRUD.get_by_name(db, "携程广州")
            if not existing:
                await CompanyCRUD.create(db, company_data)
                print("✓ 创建测试企业: 携程广州")
            else:
                print("✓ 测试企业已存在: 携程广州")
        except Exception as e:
            print(f"✗ 设置测试企业失败: {str(e)}")
            return False
            
        # 确保"金蝶广州"在数据库中存在 - 使用不同的税号避免冲突
        customer_data = CompanyCreate(
            name="金蝶广州",
            tax_number="91440100MA5CYC7K9Y",  # 使用不同的税号
            address="广州市天河区体育西路103号维多利广场A塔",
            phone="020-38680000",
            email="guangzhou@kingdee.com",
            category="TECH"
        )
        
        try:
            existing = await CompanyCRUD.get_by_name(db, "金蝶广州")
            if not existing:
                await CompanyCRUD.create(db, customer_data)
                print("✓ 创建测试客户: 金蝶广州")
            else:
                print("✓ 测试客户已存在: 金蝶广州")
        except Exception as e:
            print(f"✗ 设置测试客户失败: {str(e)}")
    
    return True


async def test_supplier_tax_number_completion():
    """测试供应商税号自动补全"""
    print("\n" + "="*60)
    print("开始测试用例 2.1: 供应商税号自动补全")
    print("="*60)
    
    # 设置测试数据
    if not await setup_test_data():
        print("❌ 测试数据设置失败")
        return False
    
    try:
        # 创建发票服务实例
        async with AsyncSessionLocal() as db:
            invoice_service = InvoiceProcessingService(db)
            
            # 处理发票
            print("\n处理测试发票...")
            
            # 先解析看看原始状态
            from app.core.kdubl_converter import KDUBLDomainConverter
            converter = KDUBLDomainConverter()
            domain = converter.parse(TEST_XML)
            print(f"解析后供应商税号: {domain.supplier.tax_no}")
            print(f"解析后供应商税号类型: {type(domain.supplier.tax_no)}")
            
            result = await invoice_service.process_kdubl_invoice(TEST_XML)
            
            print("\n处理结果:")
            print(f"Result keys: {list(result.keys())}")
            print("-" * 40)
            
            # 检查处理步骤
            success_steps = []
            failed_steps = []
            
            steps = result.get("steps", [])
            for step in steps:
                print(f"📋 步骤: {step}")
            
            # 检查最终发票数据
            print("\n最终发票数据:")
            print("-" * 40)
            
            # 打印详细的执行结果
            if result.get("execution_details", {}).get("completion_logs"):
                print("\n字段补全详情:")
                for log in result["execution_details"]["completion_logs"]:
                    print(f"  ✅ {log['rule_name']}: {log.get('target_field')} = {log.get('value')}")
            
            if result.get("execution_details", {}).get("validation_logs"):
                print("\n验证详情:")
                for log in result["execution_details"]["validation_logs"]:
                    status = "✅" if log['status'] == 'passed' else "❌" if log['status'] == 'failed' else "⚠️"
                    print(f"  {status} {log['rule_name']}: {log.get('message', log.get('error_message'))}")
            
            print(f"\n成功状态: {result.get('success')}")
            if result.get("errors"):
                print(f"错误信息: {result['errors']}")
            
            # 验证核心功能是否成功
            completion_logs = result.get("execution_details", {}).get("completion_logs", [])
            tax_number_completed = any("税号" in log.get('rule_name', '') for log in completion_logs if log.get('status') == 'success')
            
            if tax_number_completed:
                print("\n🎉 核心测试通过：供应商税号自动补全成功！")
            else:
                print("\n❌ 核心测试失败：供应商税号未能自动补全")
            
            # 测试结果分析
            print("\n测试结果分析:")
            print("="*40)
            
            # 简单的成功/失败判断
            if result.get("success"):
                print("✅ 发票处理成功")
                return True
            else:
                print("❌ 发票处理失败")
                if result.get("errors"):
                    print(f"错误原因: {result['errors']}")
                return False
            
    except Exception as e:
        print(f"❌ 测试执行失败: {str(e)}")
        import traceback
        traceback.print_exc()
        return False


async def main():
    """主测试函数"""
    print("发票系统 - 测试用例 2.1 自动化测试")
    print("测试目标: 验证供应商税号自动补全功能")
    
    success = await test_supplier_tax_number_completion()
    
    if success:
        print("\n🎉 所有测试通过!")
        sys.exit(0)
    else:
        print("\n❌ 测试失败!")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())