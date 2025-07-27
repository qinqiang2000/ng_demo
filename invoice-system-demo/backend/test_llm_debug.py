#!/usr/bin/env python3
"""
测试LLM规则生成的调试日志输出
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

async def test_llm_debug_logs():
    """测试LLM生成规则的调试日志"""
    
    print("🧪 开始测试LLM规则生成的调试日志输出")
    print("="*70)
    
    # 测试补全规则生成
    completion_request = RuleGenerationRequest(
        description="根据供应商名称从数据库查询税号，用于补全缺失的供应商税号字段",
        rule_type="completion"
    )
    
    print("📋 测试1: 补全规则生成")
    print("-"*50)
    
    try:
        llm_service = LLMService()
        result = await llm_service.generate_rule(completion_request)
        
        print(f"✅ 测试1完成，结果: success={result.get('success')}")
        if result.get('success'):
            print(f"📊 生成的规则: {result['data'].get('rule_name', 'Unknown')}")
        else:
            print(f"❌ 生成失败: {result.get('error')}")
        
        await llm_service.close()
        
    except Exception as e:
        print(f"💥 测试1异常: {e}")
    
    print("\n" + "="*70)
    
    # 测试校验规则生成
    validation_request = RuleGenerationRequest(
        description="大额发票必须有客户税号，金额超过5000元的发票客户税号不能为空",
        rule_type="validation"
    )
    
    print("📋 测试2: 校验规则生成")
    print("-"*50)
    
    try:
        llm_service = LLMService()
        result = await llm_service.generate_rule(validation_request)
        
        print(f"✅ 测试2完成，结果: success={result.get('success')}")
        if result.get('success'):
            print(f"📊 生成的规则: {result['data'].get('rule_name', 'Unknown')}")
        else:
            print(f"❌ 生成失败: {result.get('error')}")
        
        await llm_service.close()
        
    except Exception as e:
        print(f"💥 测试2异常: {e}")
    
    print("\n" + "="*70)
    print("🎉 LLM调试日志测试完成！")

if __name__ == "__main__":
    asyncio.run(test_llm_debug_logs())