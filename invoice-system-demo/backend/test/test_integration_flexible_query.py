#!/usr/bin/env python3
"""
集成测试：使用invoice1.xml和rules.yaml测试灵活查询系统
"""
import asyncio
import sys
from pathlib import Path

# 添加项目根目录到Python路径
sys.path.append(str(Path(__file__).parent))

from app.services.invoice_service import InvoiceProcessingService
from app.core.kdubl_converter import KDUBLDomainConverter
from app.models.rules import FieldCompletionRule, FieldValidationRule
from app.core.cel_engine import DatabaseCELExpressionEvaluator, DatabaseCELFieldCompletionEngine, DatabaseCELBusinessValidationEngine
import yaml


async def test_invoice_processing_with_flexible_query():
    """测试使用灵活查询系统处理发票"""
    print("=== 集成测试：灵活查询系统处理发票 ===\n")
    
    # 1. 读取测试发票XML
    print("1. 读取测试发票XML")
    xml_file = Path("data/invoice1.xml")
    if not xml_file.exists():
        print(f"   错误: 文件不存在 {xml_file}")
        return
        
    with open(xml_file, 'r', encoding='utf-8') as f:
        xml_content = f.read()
    print(f"   成功读取发票XML: {xml_file}")
    
    # 2. 转换XML为Domain Object
    print("\n2. 转换XML为Domain Object")
    converter = KDUBLDomainConverter()
    try:
        domain_obj = converter.parse(xml_content)
        print(f"   转换成功!")
        print(f"   发票号码: {domain_obj.invoice_number}")
        print(f"   供应商: {domain_obj.supplier.name}")
        print(f"   客户: {domain_obj.customer.name}")
        print(f"   总金额: {domain_obj.total_amount}")
        print(f"   当前供应商税号: {getattr(domain_obj.supplier, 'tax_no', 'None')}")
    except Exception as e:
        print(f"   转换失败: {str(e)}")
        return
    
    # 3. 读取规则配置
    print("\n3. 读取规则配置")
    rules_file = Path("config/rules.yaml")
    if not rules_file.exists():
        print(f"   错误: 规则文件不存在 {rules_file}")
        return
        
    with open(rules_file, 'r', encoding='utf-8') as f:
        rules_config = yaml.safe_load(f)
    
    # 转换为规则对象
    completion_rules = []
    for rule_data in rules_config.get('field_completion_rules', []):
        rule = FieldCompletionRule(**rule_data)
        completion_rules.append(rule)
    
    validation_rules = []
    for rule_data in rules_config.get('field_validation_rules', []):
        rule = FieldValidationRule(**rule_data)
        validation_rules.append(rule)
    
    print(f"   成功加载规则: {len(completion_rules)} 个补全规则, {len(validation_rules)} 个校验规则")
    
    # 4. 测试CEL表达式评估器
    print("\n4. 测试CEL表达式评估器")
    cel_evaluator = DatabaseCELExpressionEvaluator()
    context = {'invoice': domain_obj}
    
    # 测试几个关键表达式
    test_expressions = [
        "db_query('get_tax_number_by_name', invoice.supplier.name)",
        "db_query('get_company_category_by_name', invoice.supplier.name)",
        "db_query('get_tax_rate_by_category_and_amount', 'TRAVEL_SERVICE', invoice.total_amount)"
    ]
    
    for expr in test_expressions:
        try:
            result = await cel_evaluator.evaluate_async(expr, context)
            print(f"   表达式: {expr}")
            print(f"   结果: {result}")
        except Exception as e:
            print(f"   表达式: {expr}")
            print(f"   错误: {str(e)}")
    
    # 5. 执行字段补全
    print("\n5. 执行字段补全")
    completion_engine = DatabaseCELFieldCompletionEngine()
    completion_engine.load_rules(completion_rules)
    
    try:
        print("   执行前字段状态:")
        print(f"     供应商税号: {getattr(domain_obj.supplier, 'tax_no', 'None')}")
        print(f"     税额: {getattr(domain_obj, 'tax_amount', 'None')}")
        print(f"     净额: {getattr(domain_obj, 'net_amount', 'None')}")
        
        await completion_engine.complete_async(domain_obj)
        
        print("   执行后字段状态:")
        print(f"     供应商税号: {getattr(domain_obj.supplier, 'tax_no', 'None')}")
        print(f"     税额: {getattr(domain_obj, 'tax_amount', 'None')}")
        print(f"     净额: {getattr(domain_obj, 'net_amount', 'None')}")
        
        # 显示执行日志
        print("   补全规则执行日志:")
        for log in completion_engine.execution_log:
            status_icon = "✓" if log['status'] == 'success' else "✗" if log['status'] == 'error' else "!"
            print(f"     {status_icon} {log['rule_name']}: {log['status']}")
            if 'value' in log:
                print(f"       设置 {log.get('target_field', '')} = {log['value']}")
            if 'error' in log:
                print(f"       错误: {log['error']}")
        
    except Exception as e:
        print(f"   字段补全失败: {str(e)}")
        import traceback
        traceback.print_exc()
    
    # 6. 执行业务校验
    print("\n6. 执行业务校验")
    validation_engine = DatabaseCELBusinessValidationEngine()
    validation_engine.load_rules(validation_rules)
    
    try:
        is_valid, errors = await validation_engine.validate_async(domain_obj)
        
        if is_valid:
            print("   ✓ 业务校验通过")
        else:
            print("   ✗ 业务校验失败:")
            for error in errors:
                print(f"     - {error}")
        
        # 显示校验日志
        print("   校验规则执行日志:")
        for log in validation_engine.execution_log:
            status_icon = "✓" if log['status'] == 'passed' else "✗" if log['status'] == 'failed' else "!"
            print(f"     {status_icon} {log['rule_name']}: {log['status']}")
            if 'error_message' in log:
                print(f"       {log['error_message']}")
            if 'error' in log:
                print(f"       执行错误: {log['error']}")
        
    except Exception as e:
        print(f"   业务校验失败: {str(e)}")
        import traceback
        traceback.print_exc()
    
    # 7. 转换回XML
    print("\n7. 转换回XML")
    try:
        result_xml = converter.build(domain_obj)
        print("   ✓ 成功转换为XML")
        
        # 保存结果
        output_file = Path("data/invoice1_processed.xml")
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(result_xml)
        print(f"   结果已保存到: {output_file}")
        
    except Exception as e:
        print(f"   XML转换失败: {str(e)}")
    
    print("\n=== 集成测试完成 ===")


async def test_specific_rules():
    """测试特定的规则表达式"""
    print("\n=== 测试特定规则表达式 ===\n")
    
    # 创建测试发票对象
    from app.models.domain import InvoiceDomainObject, Party
    from datetime import date
    
    invoice = InvoiceDomainObject(
        invoice_number="TEST001",
        issue_date=date.today(),
        invoice_type="普通发票",
        supplier=Party(name="携程广州"),
        customer=Party(name="金蝶广州"),
        items=[],
        total_amount=15000
    )
    
    context = {'invoice': invoice}
    evaluator = DatabaseCELExpressionEvaluator()
    
    # 测试从rules.yaml中提取的表达式
    test_rules = [
        {
            "name": "供应商税号补全",
            "condition": "invoice.supplier.tax_no == null",
            "expression": "db_query('get_tax_number_by_name', invoice.supplier.name)"
        },
        {
            "name": "供应商分类获取",
            "condition": "!has(invoice.extensions.supplier_category)",
            "expression": "db_query('get_company_category_by_name', invoice.supplier.name)"
        },
        {
            "name": "税率计算",
            "condition": "has(invoice.extensions.supplier_category)",
            "expression": "db_query('get_tax_rate_by_category_and_amount', 'TRAVEL_SERVICE', invoice.total_amount)"
        }
    ]
    
    for rule in test_rules:
        print(f"测试规则: {rule['name']}")
        
        # 测试条件
        try:
            condition_result = await evaluator.evaluate_async(rule['condition'], context)
            print(f"  条件: {rule['condition']}")
            print(f"  条件结果: {condition_result}")
        except Exception as e:
            print(f"  条件评估错误: {str(e)}")
            continue
        
        # 测试表达式
        try:
            expr_result = await evaluator.evaluate_async(rule['expression'], context)
            print(f"  表达式: {rule['expression']}")
            print(f"  表达式结果: {expr_result}")
        except Exception as e:
            print(f"  表达式评估错误: {str(e)}")
        
        print()


if __name__ == "__main__":
    print("开始集成测试...")
    asyncio.run(test_invoice_processing_with_flexible_query())
    asyncio.run(test_specific_rules())