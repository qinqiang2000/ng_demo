#!/usr/bin/env python3
"""
Comprehensive Context Structure Demonstration

This script demonstrates the advantages of using comprehensive context structure
for LLM rule generation vs simple hardcoded prompts.
"""

import asyncio
import json
from app.services.llm_service import LLMService, RuleGenerationRequest
from app.services.llm_context_service import llm_context_service
from app.core.llm_rule_context import RuleType


async def demonstrate_context_advantages():
    """Demonstrate the advantages of comprehensive context structure"""
    
    print("🎯 Comprehensive Context Structure Demonstration")
    print("=" * 80)
    
    print("""
这个演示展示了comprehensive context structure相比硬编码提示词的优势：

1. **动态上下文生成**: 根据规则类型和目标字段动态生成相关上下文
2. **结构化信息**: 使用YAML模板文件组织语法、领域模型、数据库结构
3. **智能字段推断**: 从自然语言描述中推断目标字段
4. **上下文优化**: 最小化token使用，提供最相关的信息
5. **可扩展性**: 易于添加新的模式和功能
""")
    
    llm_service = LLMService()
    
    scenarios = [
        {
            "name": "供应商税号补全",
            "request": RuleGenerationRequest(
                description="当供应商税号为空时，根据供应商名称从企业信息表查询对应税号",
                rule_type="completion"
            ),
            "expected_benefits": [
                "自动推断target_field为tax_no",
                "提供相关的数据库表结构信息",
                "包含智能查询语法示例",
                "提供has()函数等相关CEL函数"
            ]
        },
        {
            "name": "邮箱格式校验", 
            "request": RuleGenerationRequest(
                description="验证客户邮箱格式是否符合标准邮箱格式要求",
                rule_type="validation"
            ),
            "expected_benefits": [
                "自动推断field_path为customer.email",
                "提供正则表达式相关的语法信息",
                "包含格式校验的模式示例",
                "生成适合的错误消息"
            ]
        },
        {
            "name": "动态税率计算",
            "request": RuleGenerationRequest(
                description="根据供应商行业分类从税率表查询对应税率并计算税额",
                rule_type="completion"
            ),
            "expected_benefits": [
                "推断涉及税率计算的上下文",
                "提供tax_rates表的结构信息",
                "包含多表查询的示例",
                "提供计算相关的CEL表达式模式"
            ]
        }
    ]
    
    for i, scenario in enumerate(scenarios, 1):
        print(f"\n📋 场景 {i}: {scenario['name']}")
        print("-" * 50)
        
        print("🎯 预期的context structure优势:")
        for benefit in scenario["expected_benefits"]:
            print(f"   • {benefit}")
        
        print(f"\n📝 用户描述: {scenario['request'].description}")
        
        # 生成context并展示
        rule_type = RuleType.COMPLETION if scenario['request'].rule_type == "completion" else RuleType.VALIDATION
        
        # 展示字段推断
        if rule_type == RuleType.COMPLETION:
            inferred_field = llm_service._infer_target_field(scenario['request'].description)
            print(f"🔍 推断的目标字段: {inferred_field or '未推断出'}")
        else:
            inferred_field = llm_service._infer_validation_field(scenario['request'].description) 
            print(f"🔍 推断的校验字段: {inferred_field or '未推断出'}")
        
        # 生成并展示上下文
        context = llm_context_service.generate_minimal_context(rule_type, inferred_field)
        print(f"\n📊 生成的上下文统计:")
        print(f"   • 上下文类型: {context.get('rule_type')}")
        print(f"   • 目标字段: {context.get('target_field', '通用')}")
        print(f"   • 语法示例: {len(context.get('syntax', {}).get('examples', {}))}")
        print(f"   • 字段信息: {'✅' if context.get('field_info') else '❌'}")
        print(f"   • 模式示例: {len(context.get('patterns', []))}")
        
        # 调用LLM生成规则
        try:
            result = await llm_service.generate_rule(scenario['request'])
            
            if result['success']:
                rule_data = result['data']
                print(f"\n✅ 生成的规则:")
                print(f"   • 规则名称: {rule_data['rule_name']}")
                print(f"   • 表达式: {rule_data['rule_expression'][:100]}...")
                if rule_data.get('error_message'):
                    print(f"   • 错误消息: {rule_data['error_message']}")
                
                # 分析表达式质量
                expression = rule_data['rule_expression']
                quality_indicators = []
                
                if 'has(' in expression:
                    quality_indicators.append("✅ 使用了null检查")
                if 'db.' in expression:
                    quality_indicators.append("✅ 使用了智能查询语法")
                if '.matches(' in expression:
                    quality_indicators.append("✅ 使用了正则表达式")
                if '?' in expression and ':' in expression:
                    quality_indicators.append("✅ 使用了条件表达式")
                
                if quality_indicators:
                    print(f"\n🎊 表达式质量分析:")
                    for indicator in quality_indicators:
                        print(f"   {indicator}")
            else:
                print(f"❌ 规则生成失败: {result.get('error')}")
                
        except Exception as e:
            print(f"❌ 调用错误: {str(e)}")
    
    print(f"\n\n🎉 Comprehensive Context Structure 优势总结")
    print("=" * 80)
    
    advantages = [
        "🧠 智能字段推断 - 自动识别目标字段，减少手动配置",
        "📊 动态上下文生成 - 根据场景提供最相关的信息",  
        "🎯 Token优化 - 只包含必要信息，提高效率",
        "🔧 可扩展架构 - 易于添加新模式和功能",
        "📝 结构化模板 - 使用YAML文件管理复杂信息",
        "🎨 个性化提示 - 针对不同规则类型定制prompt",
        "📈 质量提升 - 生成更准确、更符合系统规范的规则",
        "🔄 一致性保证 - 统一的上下文结构确保输出一致性"
    ]
    
    for advantage in advantages:
        print(f"  {advantage}")
    
    print(f"\n💡 这种架构使LLM能够:")
    print("  • 理解系统的完整上下文和约束")
    print("  • 生成符合业务规范的高质量规则")
    print("  • 适应不同场景的特定需求")
    print("  • 保持代码的可维护性和可扩展性")


if __name__ == "__main__":
    asyncio.run(demonstrate_context_advantages())