#!/usr/bin/env python3
"""
测试统一CEL语法功能
"""
import asyncio
import sys
import os
import logging

# 添加项目路径
sys.path.append(os.path.dirname(__file__))

from app.services.llm_service import LLMService, RuleGenerationRequest

# 设置日志级别为INFO以确保看到调试信息
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)

async def test_unified_syntax():
    """测试统一CEL语法功能"""
    
    print("🧪 测试统一CEL语法功能")
    print("="*70)
    
    # 测试用例1：补全规则
    print("📋 测试用例1: 客户邮箱补全规则")
    completion_request = RuleGenerationRequest(
        description="当customer的邮箱为空时，通过数据库补全邮箱：db.companies.email[name=invoice.customer.name]",
        rule_type="completion"
    )
    
    print(f"🔤 用户描述: {completion_request.description}")
    print("-"*50)
    
    try:
        llm_service = LLMService()
        result = await llm_service.generate_rule(completion_request)
        
        if result.get('success'):
            rule_data = result['data']
            print("✅ 补全规则生成成功!")
            print(f"📊 规则名称: {rule_data.get('rule_name')}")
            print(f"🎯 apply_to: {rule_data.get('apply_to', 'N/A')}")
            print(f"📍 target_field: {rule_data.get('target_field', 'N/A')}")
            print(f"🔧 rule_expression: {rule_data.get('rule_expression', 'N/A')}")
            print(f"⚡ priority: {rule_data.get('priority', 'N/A')}")
            
            # 验证target_field是否包含invoice前缀
            target_field = rule_data.get('target_field', '')
            if target_field.startswith('invoice.'):
                print("✅ target_field格式正确：包含invoice前缀")
            else:
                print(f"❌ target_field格式错误：{target_field}")
                
        else:
            print(f"❌ 补全规则生成失败: {result.get('error')}")
        
        print("\n" + "="*50)
        
        # 测试用例2：校验规则
        print("📋 测试用例2: 大额发票税号校验规则")
        validation_request = RuleGenerationRequest(
            description="当发票金额大于5000元时，必须校验供应商税号不能为空",
            rule_type="validation"
        )
        
        print(f"🔤 用户描述: {validation_request.description}")
        print("-"*50)
        
        result = await llm_service.generate_rule(validation_request)
        
        if result.get('success'):
            rule_data = result['data']
            print("✅ 校验规则生成成功!")
            print(f"📊 规则名称: {rule_data.get('rule_name')}")
            print(f"🎯 apply_to: {rule_data.get('apply_to', 'N/A')}")
            print(f"📍 field_path: {rule_data.get('field_path', 'N/A')}")
            print(f"🔧 rule_expression: {rule_data.get('rule_expression', 'N/A')}")
            print(f"🚨 error_message: {rule_data.get('error_message', 'N/A')}")
            print(f"⚡ priority: {rule_data.get('priority', 'N/A')}")
            
            # 验证field_path是否包含invoice前缀
            field_path = rule_data.get('field_path', '')
            if field_path.startswith('invoice.'):
                print("✅ field_path格式正确：包含invoice前缀")
            else:
                print(f"❌ field_path格式错误：{field_path}")
                
        else:
            print(f"❌ 校验规则生成失败: {result.get('error')}")
        
        await llm_service.close()
        
    except Exception as e:
        print(f"💥 测试异常: {e}")
    
    print("\n" + "="*70)
    print("🎉 统一CEL语法测试完成！")

async def test_rule_processing():
    """测试规则处理功能"""
    print("\n" + "="*70)
    print("🔄 测试规则处理功能")
    print("="*70)
    
    try:
        from app.models.domain import InvoiceDomainObject, Party
        from app.core.cel_field_completion import CELFieldCompletionEngine
        from app.core.cel_validation import CELBusinessValidationEngine
        
        # 创建测试发票对象
        invoice = InvoiceDomainObject(
            invoice_number="TEST001",
            total_amount=6000.0,
            supplier=Party(name="测试供应商", tax_no="123456789012345ABC"),
            customer=Party(name="测试客户")  # 没有邮箱
        )
        
        print("📄 创建测试发票:")
        print(f"  - 发票号: {invoice.invoice_number}")
        print(f"  - 总金额: {invoice.total_amount}")
        print(f"  - 供应商: {invoice.supplier.name}")
        print(f"  - 客户: {invoice.customer.name}")
        print(f"  - 客户邮箱: {invoice.customer.email or '(空)'}")
        
        # 测试字段补全
        print("\n🔧 测试字段补全...")
        completion_engine = CELFieldCompletionEngine()
        completed_invoice = completion_engine.complete(invoice)
        
        print("✅ 字段补全完成")
        completion_log = completion_engine.execution_log
        for log_entry in completion_log[-3:]:  # 显示最后3条日志
            print(f"  - {log_entry.get('message', '')}")
        
        # 测试业务校验
        print("\n🔍 测试业务校验...")
        validation_engine = CELBusinessValidationEngine()
        is_valid, error_messages = validation_engine.validate(completed_invoice)
        
        print(f"✅ 业务校验完成，结果: {'通过' if is_valid else '失败'}")
        if not is_valid:
            print("❌ 校验错误:")
            for error in error_messages:
                print(f"  - {error}")
        
        validation_log = validation_engine.execution_log
        for log_entry in validation_log[-3:]:  # 显示最后3条日志
            print(f"  - {log_entry.get('message', '')}")
            
    except Exception as e:
        print(f"💥 规则处理测试异常: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    asyncio.run(test_unified_syntax())
    asyncio.run(test_rule_processing())