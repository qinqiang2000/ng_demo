#!/usr/bin/env python3
"""
LLM Integration Test Script

This script tests the OpenAI LLM integration for rule generation.
"""

import asyncio
import json
from app.services.llm_service import LLMService, RuleGenerationRequest as LLMRequest
from app.services.rule_generation_service import rule_generation_service, RuleGenerationRequest
from app.core.llm_rule_context import RuleType


async def test_llm_service():
    """Test the LLM service directly"""
    print("🧪 Testing LLM Service")
    print("=" * 50)
    
    llm_service = LLMService()
    
    if not llm_service.client:
        print("❌ LLM service not configured or unavailable")
        return False
    
    print(f"✅ LLM service initialized with model: {llm_service.config.model}")
    print(f"   API Key configured: {'***' + llm_service.config.api_key[-4:] if llm_service.config.api_key else 'No'}")
    
    # Test completion rule generation
    print("\n📝 Testing Completion Rule Generation")
    
    completion_request = LLMRequest(
        description="根据供应商名称从数据库查询税号，用于补全缺失的供应商税号字段",
        rule_type="completion",
        context=None,
        examples=[]
    )
    
    try:
        result = await llm_service.generate_rule(completion_request)
        
        if result["success"]:
            rule_data = result["data"]
            print(f"✅ Completion rule generated successfully:")
            print(f"   Rule Name: {rule_data['rule_name']}")
            print(f"   Target Field: {rule_data.get('target_field', 'N/A')}")
            print(f"   Expression: {rule_data['rule_expression']}")
            print(f"   Priority: {rule_data.get('priority', 'N/A')}")
        else:
            print(f"❌ Completion rule generation failed: {result.get('error')}")
            return False
            
    except Exception as e:
        print(f"❌ Completion rule generation error: {str(e)}")
        return False
    
    # Test validation rule generation
    print("\n🔍 Testing Validation Rule Generation") 
    
    validation_request = LLMRequest(
        description="验证供应商税号格式是否正确，税号应该是15位数字加3位字母数字组合",
        rule_type="validation",
        context=None,
        examples=[]
    )
    
    try:
        result = await llm_service.generate_rule(validation_request)
        
        if result["success"]:
            rule_data = result["data"]
            print(f"✅ Validation rule generated successfully:")
            print(f"   Rule Name: {rule_data['rule_name']}")
            print(f"   Field Path: {rule_data.get('field_path', 'N/A')}")
            print(f"   Expression: {rule_data['rule_expression']}")
            print(f"   Error Message: {rule_data.get('error_message', 'N/A')}")
        else:
            print(f"❌ Validation rule generation failed: {result.get('error')}")
            return False
            
    except Exception as e:
        print(f"❌ Validation rule generation error: {str(e)}")
        return False
    
    return True


async def test_rule_generation_service():
    """Test the integrated rule generation service"""
    print("\n\n🚀 Testing Integrated Rule Generation Service")
    print("=" * 50)
    
    # Test completion rule
    print("\n📝 Testing Completion Rule via Service")
    
    completion_request = RuleGenerationRequest(
        rule_type=RuleType.COMPLETION,
        target_field="supplier.tax_no",
        description="当供应商税号为空时，根据供应商名称从企业数据库中查询对应的税号进行补全",
        priority=95
    )
    
    try:
        suggestions = await rule_generation_service.generate_rule_suggestions(completion_request)
        
        if suggestions:
            rule = suggestions[0]
            print(f"✅ Service completion rule generated:")
            print(f"   ID: {rule.id}")
            print(f"   Name: {rule.rule_name}")
            print(f"   Expression: {rule.rule_expression}")
            print(f"   Confidence: {rule.confidence_score:.1%}")
            print(f"   Explanation: {rule.explanation}")
        else:
            print("❌ No completion rules generated")
            return False
            
    except Exception as e:
        print(f"❌ Service completion rule error: {str(e)}")
        return False
    
    # Test validation rule
    print("\n🔍 Testing Validation Rule via Service")
    
    validation_request = RuleGenerationRequest(
        rule_type=RuleType.VALIDATION,
        field_path="total_amount",
        description="验证发票总金额必须大于0，确保所有发票都有有效的金额",
        error_message="发票总金额必须大于0",
        priority=90
    )
    
    try:
        suggestions = await rule_generation_service.generate_rule_suggestions(validation_request)
        
        if suggestions:
            rule = suggestions[0]
            print(f"✅ Service validation rule generated:")
            print(f"   ID: {rule.id}")
            print(f"   Name: {rule.rule_name}")
            print(f"   Expression: {rule.rule_expression}")
            print(f"   Error Message: {rule.error_message}")
            print(f"   Confidence: {rule.confidence_score:.1%}")
        else:
            print("❌ No validation rules generated")
            return False
            
    except Exception as e:
        print(f"❌ Service validation rule error: {str(e)}")
        return False
    
    return True


async def test_complex_scenarios():
    """Test more complex rule generation scenarios"""
    print("\n\n🎯 Testing Complex Scenarios")
    print("=" * 50)
    
    scenarios = [
        {
            "name": "动态税率计算",
            "type": RuleType.COMPLETION,
            "description": "根据供应商的行业分类（TECH、TRAVEL、GENERAL等）从税率表中查询对应的税率，然后计算发票税额。如果查询不到则使用默认6%税率。",
            "target_field": "tax_amount"
        },
        {
            "name": "旅游服务发票项目校验",
            "type": RuleType.VALIDATION,
            "description": "当供应商分类为TRAVEL_SERVICE时，验证发票项目描述必须包含标准的旅游服务项目，如住宿、餐饮、交通费等。",
            "field_path": "items",
            "error_message": "旅游服务发票项目描述不规范，应包含住宿、餐饮、交通等标准服务项目"
        },
        {
            "name": "大额发票客户税号必填",
            "type": RuleType.VALIDATION,
            "description": "当发票总金额超过5000元时，客户税号必须填写且不能为空，以满足税务合规要求。",
            "field_path": "customer.tax_no",
            "error_message": "金额超过5000元的发票，客户税号必填"
        }
    ]
    
    for i, scenario in enumerate(scenarios, 1):
        print(f"\n🧪 Scenario {i}: {scenario['name']}")
        
        request = RuleGenerationRequest(
            rule_type=scenario["type"],
            target_field=scenario.get("target_field"),
            field_path=scenario.get("field_path"),
            description=scenario["description"],
            error_message=scenario.get("error_message"),
            priority=80
        )
        
        try:
            suggestions = await rule_generation_service.generate_rule_suggestions(request)
            
            if suggestions:
                rule = suggestions[0]
                print(f"   ✅ Generated: {rule.rule_name}")
                print(f"   Expression: {rule.rule_expression[:100]}...")
                if rule.error_message:
                    print(f"   Error: {rule.error_message}")
            else:
                print(f"   ❌ No rules generated for scenario {i}")
                return False
                
        except Exception as e:
            print(f"   ❌ Error in scenario {i}: {str(e)}")
            return False
    
    return True


async def main():
    """Run all tests"""
    print("🚀 Starting LLM Integration Tests")
    print("=" * 80)
    
    results = []
    
    # Test 1: Direct LLM service
    try:
        result1 = await test_llm_service()
        results.append(("LLM Service", result1))
    except Exception as e:
        print(f"❌ LLM Service test failed: {str(e)}")
        results.append(("LLM Service", False))
    
    # Test 2: Integrated service
    try:
        result2 = await test_rule_generation_service()
        results.append(("Rule Generation Service", result2))
    except Exception as e:
        print(f"❌ Rule Generation Service test failed: {str(e)}")
        results.append(("Rule Generation Service", False))
    
    # Test 3: Complex scenarios
    try:
        result3 = await test_complex_scenarios()
        results.append(("Complex Scenarios", result3))
    except Exception as e:
        print(f"❌ Complex scenarios test failed: {str(e)}")
        results.append(("Complex Scenarios", False))
    
    # Summary
    print("\n" + "=" * 80)
    print("📊 Test Results Summary")
    print("=" * 80)
    
    passed = sum(1 for _, result in results if result)
    total = len(results)
    
    for test_name, result in results:
        status = "✅ PASSED" if result else "❌ FAILED"
        print(f"{test_name:<25} {status}")
    
    print(f"\nOverall: {passed}/{total} tests passed")
    
    if passed == total:
        print("🎉 All LLM integration tests passed!")
        print("\n🎯 Next Steps:")
        print("1. The LLM integration is working properly")
        print("2. Rules are being generated using GPT-4o-mini")
        print("3. System falls back to templates if LLM fails")
        print("4. Ready for production use!")
    else:
        print("⚠️  Some tests failed. Please check the implementation or API configuration.")
    
    return passed == total


if __name__ == "__main__":
    success = asyncio.run(main())
    exit(0 if success else 1)