#!/usr/bin/env python3
import sys
import os
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from app.core.kdubl_converter import KDUBLDomainConverter
from app.core.cel_engine import CELExpressionEvaluator

# 解析XML
with open('/Users/qinqiang02/colab/codespace/ai/ng_demo/data/invoice1.xml', 'r', encoding='utf-8') as f:
    kdubl_xml = f.read()

converter = KDUBLDomainConverter()
domain = converter.parse(kdubl_xml)

print('=== 原始数据检查 ===')
print(f'Items数量: {len(domain.items)}')
for i, item in enumerate(domain.items):
    print(f'Item {i+1}:')
    print(f'  description: {item.description}')
    print(f'  name: {getattr(item, "name", "未设置")}')
    print(f'  tax_rate: {getattr(item, "tax_rate", "未设置")}')
    print(f'  tax_category: {getattr(item, "tax_category", "未设置")}')
    print(f'  has name attr: {hasattr(item, "name")}')
    print(f'  has tax_category attr: {hasattr(item, "tax_category")}')

# 测试条件判断
print('\n=== 条件判断测试（修复后） ===')
evaluator = CELExpressionEvaluator()

for i, item in enumerate(domain.items):
    print(f'\nItem {i+1}: {item.description}')
    context = {'invoice': domain, 'item': item}
    
    # 测试修复后的三个规则的条件
    conditions = [
        ("补全商品标准名称", "item.name == null || item.name == ''"),
        ("补全商品税率", "item.tax_rate == null"),
        ("补全商品税种", "item.tax_category == null || item.tax_category == ''")
    ]
    
    for rule_name, condition in conditions:
        try:
            result = evaluator.evaluate(condition, context)
            print(f'  {rule_name}: {condition} => {result}')
        except Exception as e:
            print(f'  {rule_name}: {condition} => ERROR: {e}')

# 测试has()函数的具体行为
print('\n=== has()函数测试 ===')
for i, item in enumerate(domain.items):
    print(f'\nItem {i+1}:')
    context = {'invoice': domain, 'item': item}
    
    # 测试各种has()表达式
    has_tests = [
        "has(item.name)",
        "has(item.tax_rate)", 
        "has(item.tax_category)",
        "has(item.description)"
    ]
    
    for test_expr in has_tests:
        try:
            result = evaluator.evaluate(test_expr, context)
            print(f'  {test_expr} => {result}')
        except Exception as e:
            print(f'  {test_expr} => ERROR: {e}')