#!/usr/bin/env python3
"""
测试修复后的LLM apply_to字段生成
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

async def test_apply_to_fix():
    """测试修复后的apply_to字段生成"""
    
    print("🧪 测试修复后的LLM apply_to字段生成")
    print("="*70)
    
    # 测试用例：客户邮箱补全
    test_request = RuleGenerationRequest(
        description="当customer的邮箱为空时，通过数据库补全邮箱：db.companies.email[name=invoice.customer.name]",
        rule_type="completion"
    )
    
    print("📋 测试用例: 客户邮箱补全")
    print(f"🔤 用户描述: {test_request.description}")
    print("-"*50)
    
    try:
        llm_service = LLMService()
        result = await llm_service.generate_rule(test_request)
        
        if result.get('success'):
            rule_data = result['data']
            print("✅ 生成成功!")
            print(f"📊 规则名称: {rule_data.get('rule_name')}")
            print(f"🎯 apply_to: {rule_data.get('apply_to', 'N/A')}")
            print(f"🔧 rule_expression: {rule_data.get('rule_expression', 'N/A')}")
            print(f"📍 target_field: {rule_data.get('target_field', 'N/A')}")
            print(f"⚡ priority: {rule_data.get('priority', 'N/A')}")
            
            # 验证apply_to字段是否正确
            expected_apply_to = "!has(invoice.customer.email) || invoice.customer.email == null || invoice.customer.email == ''"
            actual_apply_to = rule_data.get('apply_to', '')
            
            print("\n" + "="*50)
            print("🔍 apply_to字段验证:")
            print(f"期望: {expected_apply_to}")
            print(f"实际: {actual_apply_to}")
            
            if expected_apply_to in actual_apply_to or actual_apply_to in expected_apply_to:
                print("✅ apply_to字段基本符合预期!")
            else:
                print("❌ apply_to字段与预期不符")
                
        else:
            print(f"❌ 生成失败: {result.get('error')}")
        
        await llm_service.close()
        
    except Exception as e:
        print(f"💥 测试异常: {e}")
    
    print("\n" + "="*70)
    print("🎉 apply_to修复测试完成！")

if __name__ == "__main__":
    asyncio.run(test_apply_to_fix())