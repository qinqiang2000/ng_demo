#!/usr/bin/env python3
"""
LLM Rule Generation System Demo

This script demonstrates the complete LLM rule generation workflow:
1. Context preparation for different rule types
2. Template-based rule generation (simulating LLM behavior)
3. Rule validation and improvement suggestions
"""

import json
import asyncio
from app.core.llm_rule_context import RuleType
from app.services.llm_context_service import llm_context_service
from app.services.rule_generation_service import rule_generation_service, RuleGenerationRequest


def demo_context_generation():
    """Demonstrate context generation for different scenarios"""
    print("🔧 Context Generation Demo")
    print("=" * 50)
    
    scenarios = [
        {
            "name": "Tax Number Completion",
            "rule_type": RuleType.COMPLETION,
            "target_field": "supplier.tax_no",
            "description": "Context for database lookup completion"
        },
        {
            "name": "Email Format Validation", 
            "rule_type": RuleType.VALIDATION,
            "target_field": "customer.email",
            "description": "Context for format validation rules"
        },
        {
            "name": "Tax Amount Calculation",
            "rule_type": RuleType.COMPLETION,
            "target_field": "tax_amount",
            "description": "Context for calculation-based completion"
        }
    ]
    
    for scenario in scenarios:
        print(f"\n📋 {scenario['name']}")
        print(f"   {scenario['description']}")
        
        # Generate full context
        context = llm_context_service.generate_context(
            scenario["rule_type"],
            scenario["target_field"]
        )
        
        print(f"   ✓ Generated context with {len(context.rule_patterns)} patterns")
        
        # Show relevant patterns
        for i, pattern in enumerate(context.rule_patterns[:2], 1):
            print(f"     {i}. {pattern.name}: {pattern.example}")
        
        # Generate minimal context for LLM
        minimal = llm_context_service.generate_minimal_context(
            scenario["rule_type"],
            scenario["target_field"]
        )
        
        print(f"   ✓ Minimal context: {len(str(minimal))} characters")


def demo_rule_generation():
    """Demonstrate rule generation for various business scenarios"""
    print("\n\n🤖 Rule Generation Demo")
    print("=" * 50)
    
    business_scenarios = [
        {
            "name": "Supplier Tax Number Lookup",
            "request": RuleGenerationRequest(
                rule_type=RuleType.COMPLETION,
                target_field="supplier.tax_no",
                description="根据供应商名称从数据库查询税号，用于补全缺失的税号信息",
                priority=95
            )
        },
        {
            "name": "Invoice Amount Validation",
            "request": RuleGenerationRequest(
                rule_type=RuleType.VALIDATION,
                field_path="total_amount",
                description="验证发票总金额必须大于0，确保金额的有效性",
                error_message="发票总金额必须大于0",
                priority=90
            )
        },
        {
            "name": "Tax Calculation",
            "request": RuleGenerationRequest(
                rule_type=RuleType.COMPLETION,
                target_field="tax_amount",
                description="根据供应商分类动态计算税额，支持不同行业的税率",
                priority=80
            )
        },
        {
            "name": "Email Format Check",
            "request": RuleGenerationRequest(
                rule_type=RuleType.VALIDATION,
                field_path="customer.email",
                description="使用正则表达式验证客户邮箱格式是否正确",
                error_message="客户邮箱格式错误",
                priority=70
            )
        }
    ]
    
    generated_rules = []
    
    for scenario in business_scenarios:
        print(f"\n📝 {scenario['name']}")
        print(f"   Description: {scenario['request'].description}")
        
        # Generate rule suggestions
        suggestions = rule_generation_service.generate_rule_suggestions(scenario['request'])
        
        for i, rule in enumerate(suggestions, 1):
            print(f"   {i}. Rule: {rule.rule_name}")
            print(f"      Expression: {rule.rule_expression}")
            print(f"      Confidence: {rule.confidence_score:.1%}")
            
            if rule.error_message:
                print(f"      Error Message: {rule.error_message}")
            
            generated_rules.append(rule)
    
    return generated_rules


def demo_rule_validation(rules):
    """Demonstrate rule validation and improvement suggestions"""
    print("\n\n🔍 Rule Validation Demo")
    print("=" * 50)
    
    # Test various rule expressions
    test_cases = [
        {
            "name": "Valid Database Query",
            "expression": "db.companies.tax_number[name=invoice.supplier.name]",
            "rule_type": RuleType.COMPLETION
        },
        {
            "name": "Valid Format Validation",
            "expression": "invoice.customer.email.matches('^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$')",
            "rule_type": RuleType.VALIDATION
        },
        {
            "name": "Invalid Syntax (Missing Parenthesis)",
            "expression": "has(invoice.supplier.name && invoice.supplier.name != ''",
            "rule_type": RuleType.VALIDATION
        },
        {
            "name": "Invalid Table Reference",
            "expression": "db.unknown_table.field[name=invoice.supplier.name]",
            "rule_type": RuleType.COMPLETION
        },
        {
            "name": "Performance Warning (Multiple DB Queries)",
            "expression": "db.companies.tax_number[name=invoice.supplier.name] + db.tax_rates.rate[category='GENERAL'] + db.companies.category[name=invoice.customer.name]",
            "rule_type": RuleType.COMPLETION
        }
    ]
    
    for test_case in test_cases:
        print(f"\n🧪 {test_case['name']}")
        print(f"   Expression: {test_case['expression'][:60]}...")
        
        result = rule_generation_service.validate_rule(
            test_case['expression'],
            test_case['rule_type']
        )
        
        status = "✅ Valid" if result.is_valid else "❌ Invalid"
        print(f"   Status: {status}")
        
        if result.errors:
            print(f"   Errors: {', '.join(result.errors)}")
        
        if result.warnings:
            print(f"   Warnings: {', '.join(result.warnings)}")
        
        if result.suggestions:
            print(f"   Suggestions: {', '.join(result.suggestions)}")


def demo_context_for_llm():
    """Show how context would be used in actual LLM prompts"""
    print("\n\n💬 LLM Prompt Context Demo")
    print("=" * 50)
    
    # Example scenario: Generate a tax calculation rule
    scenario = {
        "user_request": "我需要一个规则来自动计算发票税额，根据供应商的行业分类使用不同的税率",
        "rule_type": RuleType.COMPLETION,
        "target_field": "tax_amount"
    }
    
    print(f"User Request: {scenario['user_request']}")
    print(f"Rule Type: {scenario['rule_type']}")
    print(f"Target Field: {scenario['target_field']}")
    
    # Generate minimal context for LLM
    context = llm_context_service.generate_minimal_context(
        scenario['rule_type'],
        scenario['target_field']
    )
    
    # Simulate LLM prompt structure
    prompt = f"""
# 规则生成任务

## 用户需求
{scenario['user_request']}

## 目标
- 规则类型: {scenario['rule_type']}
- 目标字段: {scenario['target_field']}

## 系统上下文
```json
{json.dumps(context, indent=2, ensure_ascii=False)}
```

## 请生成规则
请根据以上上下文生成一个规则表达式，包括：
1. rule_expression: CEL表达式
2. apply_to: 应用条件
3. priority: 优先级 (1-100)
4. explanation: 规则解释

注意：
- 使用智能查询语法访问数据库
- 包含错误处理和默认值
- 确保表达式语法正确
"""
    
    print(f"\n📤 Generated LLM Prompt ({len(prompt)} characters)")
    print("=" * 30)
    print(prompt[:500] + "..." if len(prompt) > 500 else prompt)
    
    # Show what the context contains
    print(f"\n📊 Context Statistics")
    print(f"   Syntax examples: {len(context.get('syntax', {}).get('examples', {}))}")
    print(f"   Field info available: {'field_info' in context}")
    print(f"   Database tables: {len(context.get('database', {}).get('tables', {}))}")
    print(f"   Pattern examples: {len(context.get('patterns', []))}")


async def main():
    """Run the complete demo"""
    print("🚀 LLM Rule Generation System Demo")
    print("This demo shows how the system prepares context for LLM-based rule generation")
    print("=" * 80)
    
    # Step 1: Context Generation
    demo_context_generation()
    
    # Step 2: Rule Generation  
    generated_rules = demo_rule_generation()
    
    # Step 3: Rule Validation
    demo_rule_validation(generated_rules)
    
    # Step 4: LLM Context Demo
    demo_context_for_llm()
    
    print("\n\n🎯 Demo Summary")
    print("=" * 50)
    print("✅ Context generation for different rule types")
    print("✅ Template-based rule generation (simulating LLM)")
    print("✅ Rule validation with error detection")
    print("✅ LLM prompt context preparation")
    print("\n🎉 The LLM Rule Generation System is ready for integration!")
    print("\nNext steps:")
    print("1. Integrate with actual LLM API (OpenAI, Claude, etc.)")
    print("2. Add more sophisticated rule patterns")
    print("3. Implement rule learning from user feedback")
    print("4. Add rule performance monitoring")


if __name__ == "__main__":
    asyncio.run(main())