#!/usr/bin/env python3
"""
LLM Rule Generation System Test Script

This script tests all components of the LLM rule generation system:
- Context generation
- Rule suggestion generation  
- Rule validation
- API endpoints
"""

import asyncio
import json
from typing import Dict, Any

from app.core.llm_rule_context import RuleType
from app.services.llm_context_service import llm_context_service
from app.services.rule_generation_service import (
    rule_generation_service, 
    RuleGenerationRequest
)
from app.api.endpoints.rule_generation import RuleValidationRequest


def test_context_generation():
    """Test LLM context generation"""
    print("=== Testing Context Generation ===")
    
    # Test completion rule context
    completion_context = llm_context_service.generate_context(
        RuleType.COMPLETION, 
        target_field="supplier.tax_no"
    )
    
    print(f"✓ Completion context generated")
    print(f"  - Domain fields: {len(completion_context.domain_fields)}")
    print(f"  - Database tables: {len(completion_context.database_tables)}")
    print(f"  - Rule patterns: {len(completion_context.rule_patterns)}")
    print(f"  - Hints: {len(completion_context.hints)}")
    
    # Test validation rule context
    validation_context = llm_context_service.generate_context(
        RuleType.VALIDATION,
        target_field="customer.email"
    )
    
    print(f"✓ Validation context generated")
    print(f"  - Rule patterns: {len(validation_context.rule_patterns)}")
    
    # Test minimal context
    minimal_context = llm_context_service.generate_minimal_context(
        RuleType.COMPLETION,
        target_field="tax_amount"
    )
    
    print(f"✓ Minimal context generated")
    print(f"  - Context keys: {list(minimal_context.keys())}")
    
    return True


def test_rule_generation():
    """Test rule generation functionality"""
    print("\n=== Testing Rule Generation ===")
    
    # Test completion rule generation
    completion_request = RuleGenerationRequest(
        rule_type=RuleType.COMPLETION,
        target_field="supplier.tax_no",
        description="从数据库根据供应商名称查询税号",
        priority=90
    )
    
    completion_suggestions = rule_generation_service.generate_rule_suggestions(completion_request)
    print(f"✓ Generated {len(completion_suggestions)} completion rules")
    
    for i, rule in enumerate(completion_suggestions, 1):
        print(f"  {i}. {rule.rule_name}")
        print(f"     Expression: {rule.rule_expression}")
        print(f"     Confidence: {rule.confidence_score}")
    
    # Test validation rule generation
    validation_request = RuleGenerationRequest(
        rule_type=RuleType.VALIDATION,
        field_path="supplier.tax_no",
        description="校验供应商税号格式是否正确",
        error_message="供应商税号格式错误",
        priority=85
    )
    
    validation_suggestions = rule_generation_service.generate_rule_suggestions(validation_request)
    print(f"✓ Generated {len(validation_suggestions)} validation rules")
    
    for i, rule in enumerate(validation_suggestions, 1):
        print(f"  {i}. {rule.rule_name}")
        print(f"     Expression: {rule.rule_expression}")
        print(f"     Error message: {rule.error_message}")
    
    return completion_suggestions + validation_suggestions


def test_rule_validation():
    """Test rule validation functionality"""
    print("\n=== Testing Rule Validation ===")
    
    # Test valid rule
    valid_rule = "has(invoice.supplier.name) && invoice.supplier.name != ''"
    result = rule_generation_service.validate_rule(valid_rule, RuleType.VALIDATION)
    
    print(f"✓ Valid rule validation: {result.is_valid}")
    print(f"  - Errors: {len(result.errors)}")
    print(f"  - Warnings: {len(result.warnings)}")
    print(f"  - Suggestions: {len(result.suggestions)}")
    
    # Test invalid rule
    invalid_rule = "has(invoice.supplier.name && invoice.supplier.name != ''"  # Missing parenthesis
    result = rule_generation_service.validate_rule(invalid_rule, RuleType.VALIDATION)
    
    print(f"✓ Invalid rule validation: {result.is_valid}")
    print(f"  - Errors: {result.errors}")
    
    # Test smart query validation
    smart_query_rule = "db.unknown_table.field[name=invoice.supplier.name]"
    result = rule_generation_service.validate_rule(smart_query_rule, RuleType.COMPLETION)
    
    print(f"✓ Smart query validation: {result.is_valid}")
    if result.errors:
        print(f"  - Errors: {result.errors}")
    
    return True


async def test_api_endpoints():
    """Test API endpoints (simulated)"""
    print("\n=== Testing API Endpoints ===")
    
    try:
        # Import API functions
        from app.api.endpoints.rule_generation import (
            generate_rule_suggestions,
            validate_rule,
            get_rule_context
        )
        
        # Test rule suggestion endpoint
        request = RuleGenerationRequest(
            rule_type=RuleType.COMPLETION,
            target_field="extensions.supplier_category",
            description="从数据库查询供应商分类"
        )
        
        suggestions = await generate_rule_suggestions(request)
        print(f"✓ API rule generation: {len(suggestions)} suggestions")
        
        # Test validation endpoint
        validation_request = RuleValidationRequest(
            rule_expression="has(invoice.invoice_number) && invoice.invoice_number != ''",
            rule_type=RuleType.VALIDATION
        )
        
        validation_result = await validate_rule(validation_request)
        print(f"✓ API rule validation: {validation_result.is_valid}")
        
        # Test context endpoint
        context = await get_rule_context(RuleType.COMPLETION, target_field="tax_amount")
        print(f"✓ API context generation: {len(context.get('domain_fields', []))} domain fields")
        
        return True
        
    except Exception as e:
        print(f"✗ API test failed: {str(e)}")
        return False


def test_pattern_examples():
    """Test pattern examples and references"""
    print("\n=== Testing Pattern Examples ===")
    
    # Test syntax reference
    syntax_ref = llm_context_service.get_syntax_reference()
    print(f"✓ Syntax reference loaded: {len(syntax_ref)} sections")
    
    # Test domain reference
    domain_ref = llm_context_service.get_domain_reference()
    print(f"✓ Domain reference loaded: {len(domain_ref)} sections")
    
    # Test database reference
    db_ref = llm_context_service.get_database_reference()
    print(f"✓ Database reference loaded: {len(db_ref)} sections")
    
    # Test pattern reference
    pattern_ref = llm_context_service.get_pattern_reference()
    print(f"✓ Pattern reference loaded: {len(pattern_ref)} sections")
    
    if 'completion_patterns' in pattern_ref:
        completion_patterns = pattern_ref['completion_patterns']
        print(f"  - Completion patterns: {len(completion_patterns)}")
        
    if 'validation_patterns' in pattern_ref:
        validation_patterns = pattern_ref['validation_patterns']
        print(f"  - Validation patterns: {len(validation_patterns)}")
    
    return True


def test_rule_analysis():
    """Test existing rule analysis"""
    print("\n=== Testing Rule Analysis ===")
    
    # Sample existing rules
    sample_rules = [
        {
            "id": "completion_001",
            "rule_name": "设置默认国家",
            "rule_type": "completion",
            "rule_expression": "'CN'",
            "target_field": "country"
        },
        {
            "id": "completion_002",
            "rule_name": "从DB补全供应商税号",
            "rule_type": "completion",
            "rule_expression": "db.companies.tax_number[name=invoice.supplier.name]",
            "target_field": "supplier.tax_no"
        },
        {
            "id": "validation_001",
            "rule_name": "发票号码必填",
            "rule_type": "validation",
            "rule_expression": "has(invoice.invoice_number) && invoice.invoice_number != ''",
            "field_path": "invoice_number"
        }
    ]
    
    analysis = rule_generation_service.analyze_existing_rules(sample_rules)
    
    print(f"✓ Rule analysis completed")
    print(f"  - Total rules: {analysis['total_rules']}")
    print(f"  - Completion rules: {analysis['completion_rules']}")
    print(f"  - Validation rules: {analysis['validation_rules']}")
    print(f"  - Database queries: {analysis['database_queries']}")
    print(f"  - Suggestions: {len(analysis['suggestions'])}")
    
    if analysis['suggestions']:
        for suggestion in analysis['suggestions']:
            print(f"    • {suggestion}")
    
    return True


async def run_all_tests():
    """Run all tests"""
    print("🚀 Starting LLM Rule Generation System Tests\n")
    
    tests = [
        ("Context Generation", test_context_generation),
        ("Rule Generation", test_rule_generation),
        ("Rule Validation", test_rule_validation),
        ("API Endpoints", test_api_endpoints),
        ("Pattern Examples", test_pattern_examples),
        ("Rule Analysis", test_rule_analysis),
    ]
    
    results = {}
    
    for test_name, test_func in tests:
        try:
            if asyncio.iscoroutinefunction(test_func):
                result = await test_func()
            else:
                result = test_func()
            results[test_name] = result
        except Exception as e:
            print(f"✗ {test_name} failed: {str(e)}")
            results[test_name] = False
    
    # Summary
    print("\n" + "="*50)
    print("📊 Test Results Summary")
    print("="*50)
    
    passed = sum(1 for r in results.values() if r)
    total = len(results)
    
    for test_name, result in results.items():
        status = "✅ PASSED" if result else "❌ FAILED"
        print(f"{test_name:<20} {status}")
    
    print(f"\nOverall: {passed}/{total} tests passed")
    
    if passed == total:
        print("🎉 All tests passed! LLM Rule Generation System is ready!")
    else:
        print("⚠️  Some tests failed. Please check the implementation.")
    
    return passed == total


if __name__ == "__main__":
    success = asyncio.run(run_all_tests())
    exit(0 if success else 1)