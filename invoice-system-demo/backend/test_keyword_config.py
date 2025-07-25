#!/usr/bin/env python3
"""
测试关键词配置功能
"""
import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), 'app'))

from services.product_api_service import ProductAPIService

def test_keyword_matching():
    """测试关键词匹配功能"""
    print("=== 测试关键词匹配功能 ===")
    
    test_cases = [
        ("住宿费用", "住宿费", "增值税专票", 0.13),
        ("餐饮服务", "餐饮", "增值税普票", 0.06),
        ("停车场费用", "停车费", "不动产租赁", 0.09),
        ("会议室租赁", "会议费", "增值税", 0.06),  # 应该匹配原有数据库
        ("其他费用", "其他费用", "增值税", 0.06),  # 默认值
    ]
    
    for description, expected_name, expected_category, expected_rate in test_cases:
        result = ProductAPIService.get_product_info(description)
        print(f"\n描述: {description}")
        print(f"标准名称: {result['standard_name']} (期望: {expected_name})")
        print(f"税种: {result['tax_category']} (期望: {expected_category})")
        print(f"税率: {result['tax_rate']} (期望: {expected_rate})")
        print(f"匹配: {'✓' if result['standard_name'] == expected_name else '✗'}")

def test_config_management():
    """测试配置管理功能"""
    print("\n\n=== 测试配置管理功能 ===")
    
    # 添加新配置
    ProductAPIService.add_keyword_config("办公", "办公用品", 0.13, "增值税专票", "OFFICE")
    print("添加新配置: 办公 -> 办公用品")
    
    # 测试新配置
    result = ProductAPIService.get_product_info("办公用品采购")
    print(f"测试结果: {result['standard_name']}, {result['tax_category']}, {result['tax_rate']}")
    
    # 更新配置
    ProductAPIService.update_keyword_config("办公", tax_rate=0.06, tax_category="增值税普票")
    print("更新配置: 办公 -> 税率改为0.06, 税种改为增值税普票")
    
    # 再次测试
    result = ProductAPIService.get_product_info("办公用品采购")
    print(f"更新后结果: {result['standard_name']}, {result['tax_category']}, {result['tax_rate']}")
    
    # 查看所有配置
    print("\n当前所有关键词配置:")
    configs = ProductAPIService.get_keyword_configs()
    for keyword, config in configs.items():
        print(f"  {keyword}: {config['standard_name']} - {config['tax_category']} - {config['tax_rate']}")
    
    # 删除配置
    ProductAPIService.remove_keyword_config("办公")
    print("\n删除配置: 办公")
    
    # 验证删除
    result = ProductAPIService.get_product_info("办公用品采购")
    print(f"删除后结果: {result['standard_name']}, {result['tax_category']}, {result['tax_rate']}")

if __name__ == "__main__":
    test_keyword_matching()
    test_config_management()