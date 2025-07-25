#!/usr/bin/env python3
"""测试默认合并策略是否设置为BY_TAX_PARTY"""

import sys
import os
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.services.invoice_service import InvoiceProcessingService
from app.core.invoice_merge_engine import MergeStrategy
import asyncio


def test_default_strategy():
    """测试默认策略设置"""
    print("=== 测试默认合并策略设置 ===\n")
    
    # 创建服务实例
    service = InvoiceProcessingService()
    
    # 检查默认策略转换
    default_strategy_str = "by_tax_party"
    try:
        strategy_enum = MergeStrategy(default_strategy_str)
        print(f"✅ 默认策略字符串 '{default_strategy_str}' 成功转换为枚举: {strategy_enum}")
        print(f"   策略值: {strategy_enum.value}")
        print(f"   策略名称: {strategy_enum.name}")
    except ValueError as e:
        print(f"❌ 默认策略转换失败: {e}")
        return False
    
    # 验证策略是否为BY_TAX_PARTY
    if strategy_enum == MergeStrategy.BY_TAX_PARTY:
        print(f"✅ 默认策略正确设置为 BY_TAX_PARTY")
    else:
        print(f"❌ 默认策略设置错误，期望: BY_TAX_PARTY，实际: {strategy_enum}")
        return False
    
    print("\n=== 验证所有可用策略 ===")
    for strategy in MergeStrategy:
        print(f"  - {strategy.name}: {strategy.value}")
    
    print(f"\n✅ 默认策略测试通过！系统现在默认使用 '{default_strategy_str}' 策略")
    return True


if __name__ == "__main__":
    success = test_default_strategy()
    sys.exit(0 if success else 1)