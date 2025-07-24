#!/usr/bin/env python3
"""
测试灵活的数据库查询系统
"""
import asyncio
from app.core.flexible_db_query import FlexibleDatabaseQuery, db_query
from app.core.database_rule_engine import DatabaseExpressionEvaluator
from app.models.domain import InvoiceDomainObject, Party


async def test_flexible_query():
    """测试灵活查询系统"""
    print("=== 测试灵活的数据库查询系统 ===\n")
    
    # 1. 测试直接查询
    print("1. 测试直接查询:")
    result = await db_query('get_tax_number_by_name', '携程广州')
    print(f"   查询携程广州税号: {result}")
    
    result = await db_query('get_company_category_by_name', '金蝶广州')
    print(f"   查询金蝶广州分类: {result}")
    
    result = await db_query('get_tax_rate_by_category_and_amount', 'TRAVEL_SERVICE', 15000)
    print(f"   查询旅游服务15000元税率: {result}")
    
    # 2. 测试查询不存在的数据
    print("\n2. 测试查询不存在的数据:")
    result = await db_query('get_tax_number_by_name', '不存在的公司')
    print(f"   查询不存在的公司: {result}")
    
    # 3. 测试在规则引擎中使用
    print("\n3. 测试在规则引擎表达式中使用:")
    evaluator = DatabaseExpressionEvaluator()
    
    # 创建测试上下文
    from datetime import date
    invoice = InvoiceDomainObject(
        invoice_number="TEST001",
        issue_date=date.today(),
        invoice_type="普通发票",
        supplier=Party(name="携程广州"),
        customer=Party(name="金蝶广州"),
        items=[],
        total_amount=20000
    )
    
    context = {'invoice': invoice}
    
    # 测试表达式求值
    expressions = [
        "db_query('get_tax_number_by_name', invoice.supplier.name)",
        "db_query('get_company_category_by_name', invoice.customer.name)",
        "db_query('get_tax_rate_by_category_and_amount', 'SOFTWARE', invoice.total_amount)",
        "invoice.total_amount * db_query('get_tax_rate_by_category_and_amount', 'SOFTWARE', invoice.total_amount)"
    ]
    
    for expr in expressions:
        result = await evaluator.evaluate(expr, context)
        print(f"   表达式: {expr}")
        print(f"   结果: {result}")
    
    # 4. 测试查询处理器功能
    print("\n4. 测试查询处理器功能:")
    handler = FlexibleDatabaseQuery()
    
    # 获取所有可用查询
    queries = handler.get_available_queries()
    print(f"   可用查询数量: {len(queries)}")
    for query in queries[:3]:  # 只显示前3个
        print(f"   - {query['name']}: {query['description']}")
    
    # 5. 测试配置文件不存在的情况
    print("\n5. 测试配置文件不存在的情况:")
    handler2 = FlexibleDatabaseQuery("nonexistent.yaml")
    try:
        result = await handler2.query('get_tax_number_by_name', '携程广州')
        print(f"   配置文件不存在时的查询结果: {result}")
    except ValueError as e:
        print(f"   预期的错误: {e}")
    
    print("\n=== 测试完成 ===")


async def test_rule_with_flexible_query():
    """测试在规则中使用灵活查询"""
    print("\n=== 测试在规则中使用灵活查询 ===\n")
    
    from app.core.database_rule_engine import DatabaseFieldCompletionEngine
    from app.models.rules import FieldCompletionRule
    
    # 创建测试发票
    from datetime import date
    invoice = InvoiceDomainObject(
        invoice_number="TEST002",
        issue_date=date.today(),
        invoice_type="普通发票",
        supplier=Party(name="携程广州"),
        customer=Party(name="金蝶广州"),
        items=[],
        total_amount=50000
    )
    
    # 创建规则引擎
    engine = DatabaseFieldCompletionEngine()
    
    # 创建测试规则
    rules = [
        FieldCompletionRule(
            id="test_001",
            rule_name="补全供应商税号",
            apply_to="!has(invoice.supplier.tax_no)",
            target_field="supplier.tax_no",
            rule_expression="db_query('get_tax_number_by_name', invoice.supplier.name)",
            priority=100,
            active=True
        ),
        FieldCompletionRule(
            id="test_002",
            rule_name="获取供应商分类",
            apply_to="",
            target_field="extensions.supplier_category",
            rule_expression="db_query('get_company_category_by_name', invoice.supplier.name)",
            priority=90,
            active=True
        ),
        FieldCompletionRule(
            id="test_003",
            rule_name="计算税额",
            apply_to="has(invoice.extensions.supplier_category)",
            target_field="tax_amount",
            rule_expression="invoice.total_amount * db_query('get_tax_rate_by_category_and_amount', invoice.extensions.supplier_category, invoice.total_amount)",
            priority=80,
            active=True
        )
    ]
    
    # 加载规则
    engine.load_rules(rules)
    
    print("执行前:")
    print(f"  供应商: {invoice.supplier.name}")
    print(f"  供应商税号: {getattr(invoice.supplier, 'tax_no', 'None')}")
    print(f"  总金额: {invoice.total_amount}")
    print(f"  税额: {getattr(invoice, 'tax_amount', 'None')}")
    
    # 执行补全
    await engine.complete(invoice)
    
    print("\n执行后:")
    print(f"  供应商税号: {getattr(invoice.supplier, 'tax_no', 'None')}")
    print(f"  供应商分类: {getattr(invoice.extensions, 'supplier_category', 'None') if hasattr(invoice, 'extensions') else 'None'}")
    print(f"  税额: {getattr(invoice, 'tax_amount', 'None')}")
    
    print("\n执行日志:")
    for log in engine.execution_log:
        print(f"  - {log['rule_name']}: {log['status']}")
        if log['status'] == 'success' and 'value' in log:
            print(f"    设置 {log['target_field']} = {log['value']}")
        elif log['status'] == 'error':
            print(f"    错误: {log.get('error_message', 'Unknown error')}")
    
    print("\n=== 测试完成 ===")


if __name__ == "__main__":
    asyncio.run(test_flexible_query())
    asyncio.run(test_rule_with_flexible_query())