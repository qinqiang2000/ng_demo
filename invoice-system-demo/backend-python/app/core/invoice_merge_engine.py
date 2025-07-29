"""发票合并拆分引擎 - 核心算法实现"""
from typing import List, Dict, Any, Optional
from ..models.domain import InvoiceDomainObject
from enum import Enum
from ..utils.logger import get_logger
from datetime import datetime

# 创建logger
logger = get_logger(__name__)


class MergeStrategy(Enum):
    """合并策略枚举"""
    NONE = "none"  # 不合并，保持原样
    BY_TAX_PARTY = "by_tax_party"  # 按购方销方税号合并（新增的业务逻辑）

class InvoiceMergeEngine:
    """发票合并拆分引擎 - 核心算法实现"""
    
    def __init__(self):
        self.execution_log = []
    
    def _add_log(self, level: str, operation: str, message: str, details: Optional[Dict[str, Any]] = None):
        """添加日志条目
        
        Args:
            level: 日志级别 (INFO, WARN, ERROR)
            operation: 操作类型
            message: 日志消息
            details: 详细信息
        """
        log_entry = {
            "timestamp": self._get_timestamp(),
            "level": level,
            "operation": operation,
            "message": message,
            "details": details or {}
        }
        self.execution_log.append(log_entry)
    
    def _get_timestamp(self) -> str:
        """获取当前时间戳"""
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    def merge_and_split(
        self, 
        invoices: List[InvoiceDomainObject], 
        strategy: MergeStrategy = MergeStrategy.BY_TAX_PARTY,
        merge_config: Optional[Dict[str, Any]] = None,
        split_config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """合并拆分发票（一体化处理）- 固定使用BY_TAX_PARTY策略
        
        Args:
            invoices: 待处理的发票列表
            strategy: 合并策略（忽略，固定使用BY_TAX_PARTY）
            merge_config: 合并配置参数
            split_config: 拆分配置参数
            
        Returns:
            合并拆分后的发票列表
        """
        self.execution_log = []
        
        # 固定使用BY_TAX_PARTY策略，忽略外部传入的策略参数
        fixed_strategy = MergeStrategy.BY_TAX_PARTY
        
        # 添加开始日志
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "merge_and_split_start",
            "message": f"开始执行合并拆分一体化处理",
            "details": {
                "input_count": len(invoices),
                "strategy": fixed_strategy.value,
                "merge_config": merge_config,
                "split_config": split_config,
                "note": "使用固定的BY_TAX_PARTY策略"
            }
        })
        
        logger.info(f"开始合并拆分处理，输入发票数量: {len(invoices)}, 策略: {fixed_strategy.value} (固定策略)")
        
        # 第一步：执行合并
        merged_invoices = self._execute_merge(invoices, fixed_strategy, merge_config)
        
        # 第二步：执行拆分（处理尾差等情况）
        processed_invoices = self._execute_split(merged_invoices, split_config)
        
        # 添加完成日志
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "merge_and_split_complete",
            "message": f"合并拆分处理完成",
            "details": {
                "input_count": len(invoices),
                "merged_count": len(merged_invoices),
                "output_count": len(processed_invoices),
                "strategy": fixed_strategy.value
            }
        })
        
        logger.info(f"合并拆分处理完成，输入{len(invoices)}张，输出{len(processed_invoices)}张发票")
        return processed_invoices
    
    def merge(
        self, 
        invoices: List[InvoiceDomainObject], 
        strategy: MergeStrategy,
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """合并发票（单独执行合并）- 固定使用BY_TAX_PARTY策略
        
        Args:
            invoices: 待合并的发票列表
            strategy: 合并策略（忽略，固定使用BY_TAX_PARTY）
            config: 合并配置参数
            
        Returns:
            合并后的发票列表
        """
        # 固定使用BY_TAX_PARTY策略，忽略外部传入的策略参数
        fixed_strategy = MergeStrategy.BY_TAX_PARTY
        return self._execute_merge(invoices, fixed_strategy, config)
    
    def split(
        self, 
        invoices: List[InvoiceDomainObject],
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """拆分发票（单独执行拆分）
        
        Args:
            invoices: 待拆分的发票列表
            config: 拆分配置参数
            
        Returns:
            拆分后的发票列表
        """
        return self._execute_split(invoices, config)
    
    def _execute_merge(
        self, 
        invoices: List[InvoiceDomainObject], 
        strategy: MergeStrategy = MergeStrategy.BY_TAX_PARTY,
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """执行合并操作 - 固定使用BY_TAX_PARTY策略
        
        Args:
            invoices: 待合并的发票列表
            strategy: 合并策略（忽略，固定使用BY_TAX_PARTY）
            config: 合并配置参数
            
        Returns:
            合并后的发票列表
        """
        # 固定使用BY_TAX_PARTY策略，忽略外部传入的策略参数
        fixed_strategy = MergeStrategy.BY_TAX_PARTY
        
        print(f"\n🔄 开始执行合并操作")
        print(f"合并策略: {fixed_strategy.value} (固定策略)")
        print(f"输入发票数量: {len(invoices)}")
        print(f"配置参数: {config}")
        
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "merge_start",
            "message": f"开始执行合并策略: {fixed_strategy.value} (固定策略)",
            "details": {
                "strategy": fixed_strategy.value,
                "input_count": len(invoices),
                "config": config,
                "note": "使用固定的BY_TAX_PARTY策略"
            }
        })
        
        logger.debug(f"执行合并策略: {fixed_strategy.value}, 发票数量: {len(invoices)}")
        
        # 直接执行BY_TAX_PARTY合并逻辑
        result_invoices = self._merge_by_tax_party(invoices, config)
        
        print(f"\n✅ 合并操作完成")
        print(f"输出发票数量: {len(result_invoices)}")
        print(f"合并数量: {len(invoices) - len(result_invoices)}")
        
        # 添加合并完成日志
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "merge_complete",
            "message": f"合并策略执行完成: {fixed_strategy.value}",
            "details": {
                "strategy": fixed_strategy.value,
                "input_count": len(invoices),
                "output_count": len(result_invoices),
                "merged_count": len(invoices) - len(result_invoices)
            }
        })
        
        return result_invoices
    
    def _execute_split(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """执行拆分操作
        
        Args:
            invoices: 待拆分的发票列表
            config: 拆分配置参数
            
        Returns:
            拆分后的发票列表
        """
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "split_start",
            "message": "开始执行发票拆分操作",
            "details": {
                "input_count": len(invoices),
                "config": config
            }
        })
        
        logger.debug(f"执行拆分操作, 发票数量: {len(invoices)}")
        
        result_invoices = []
        for invoice in invoices:
            split_invoices = self._split_by_tax_category(invoice)
            result_invoices.extend(split_invoices)
        
        # 添加拆分完成日志
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "split_complete",
            "message": "发票拆分操作完成",
            "details": {
                "input_count": len(invoices),
                "output_count": len(result_invoices),
                "split_count": len(result_invoices) - len(invoices)
            }
        })
        
        return result_invoices
    
    def _split_by_tax_category(self, invoice: InvoiceDomainObject) -> List[InvoiceDomainObject]:
        """按税种拆分单张发票
        
        Args:
            invoice: 待拆分的发票
            
        Returns:
            拆分后的发票列表
        """
        # 按税种分组发票明细
        tax_category_groups = {}
        for item in invoice.items:
            tax_category = item.tax_category or "未分类"
            if tax_category not in tax_category_groups:
                tax_category_groups[tax_category] = []
            tax_category_groups[tax_category].append(item)
        
        # 如果只有一个税种，不需要拆分
        if len(tax_category_groups) <= 1:
            return [invoice]
        
        # 为每个税种创建一张新发票
        split_invoices = []
        for tax_category, items in tax_category_groups.items():
            split_invoice = self._create_split_invoice(invoice, items, tax_category)
            split_invoices.append(split_invoice)
        
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "split_by_tax_category",
            "message": f"发票 {invoice.invoice_number} 按税种拆分为 {len(tax_category_groups)} 张发票",
            "details": {
                "original_invoice": invoice.invoice_number,
                "tax_categories": list(tax_category_groups.keys()),
                "split_count": len(split_invoices)
            }
        })
        
        return split_invoices
    
    def _create_split_invoice(self, original_invoice: InvoiceDomainObject, items: List, tax_category: str) -> InvoiceDomainObject:
        """创建拆分后的发票
        
        Args:
            original_invoice: 原始发票
            items: 该税种的明细行列表
            tax_category: 税种
            
        Returns:
            拆分后的发票
        """
        from copy import deepcopy
        from decimal import Decimal
        
        # 深拷贝原始发票
        split_invoice = deepcopy(original_invoice)
        
        # 设置新的明细行
        split_invoice.items = items
        
        # 重新计算金额
        total_amount = sum(item.amount for item in items)
        total_tax_amount = sum(item.tax_amount or Decimal('0') for item in items)
        
        split_invoice.total_amount = total_amount
        split_invoice.tax_amount = total_tax_amount
        split_invoice.net_amount = total_amount - total_tax_amount
        
        # 更新发票号码，加上税种后缀
        split_invoice.invoice_number = f"{original_invoice.invoice_number}_{tax_category}"
        
        return split_invoice
    
    def _no_merge(self, invoices: List[InvoiceDomainObject]) -> List[InvoiceDomainObject]:
        """不合并策略 - 保持原样"""
        self._add_log("INFO", "merge_strategy_execution", "执行不合并策略，保持原始发票结构", {
            "strategy": "no_merge",
            "input_count": len(invoices)
        })
        logger.debug("执行不合并策略")
        return invoices
    
    def _merge_by_customer(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """按客户合并发票 - 实现购方销方税号相同的发票合并"""
        self._add_log("INFO", "merge_strategy_execution", "执行按客户合并策略", {
            "strategy": "by_customer",
            "input_count": len(invoices)
        })
        logger.debug("执行按客户合并策略")
        
        if not invoices:
            return invoices
        
        # 按购方和销方税号分组
        invoice_groups = {}
        for invoice in invoices:
            # 创建分组键：购方税号 + 销方税号
            customer_tax_no = invoice.customer.tax_no or ""
            supplier_tax_no = invoice.supplier.tax_no or ""
            group_key = f"{customer_tax_no}_{supplier_tax_no}"
            
            if group_key not in invoice_groups:
                invoice_groups[group_key] = []
            invoice_groups[group_key].append(invoice)
        
        merged_invoices = []
        for group_key, group_invoices in invoice_groups.items():
            if len(group_invoices) == 1:
                # 只有一张发票，不需要合并
                merged_invoices.append(group_invoices[0])
                continue
            
            # 合并同组的发票
            merged_invoice = self._merge_invoice_group(group_invoices)
            merged_invoices.append(merged_invoice)
            
            self._add_log("INFO", "merge_group_result", f"合并了{len(group_invoices)}张发票到一张发票，分组键: {group_key}", {
                "group_key": group_key,
                "input_count": len(group_invoices),
                "output_count": 1
            })
        
        logger.debug(f"按客户合并完成，原{len(invoices)}张发票合并为{len(merged_invoices)}张")
        return merged_invoices
    
    def _merge_by_supplier(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """按供应商合并发票"""
        self._add_log("INFO", "merge_strategy_execution", "执行按供应商合并策略", {
            "strategy": "by_supplier",
            "input_count": len(invoices)
        })
        logger.debug("执行按供应商合并策略")
        
        # TODO: 实现按供应商合并逻辑
        
        self._add_log("WARN", "merge_strategy_not_implemented", "按供应商合并功能暂未实现，返回原始发票列表", {
            "strategy": "by_supplier"
        })
        return invoices
    
    def _merge_by_date(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """按日期合并发票"""
        self._add_log("INFO", "merge_strategy_execution", "执行按日期合并策略", {
            "strategy": "by_date",
            "input_count": len(invoices)
        })
        logger.debug("执行按日期合并策略")
        
        # TODO: 实现按日期合并逻辑
        
        self._add_log("WARN", "merge_strategy_not_implemented", "按日期合并功能暂未实现，返回原始发票列表", {
            "strategy": "by_date"
        })
        return invoices
    
    def _merge_by_tax_party(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """按购方销方税号合并发票 - 专门的业务逻辑实现
        
        实现您要求的合并逻辑：
        1. 如果发票头的购方且销方税号一样，合并为同一张票
        2. 如果发票行中，税率 && name && tax_category 一样，则合并，amount和tax_amount和quantity要相加
        """
        self._add_log("INFO", "merge_strategy_execution", "执行按购方销方税号合并策略", {
            "strategy": "by_tax_party",
            "input_count": len(invoices)
        })
        logger.debug("执行按购方销方税号合并策略")
        
        print(f"\n\n=== 开始按购方销方税号合并 ===")
        print(f"输入发票数量: {len(invoices)}")
        
        if not invoices:
            print("发票列表为空，直接返回")
            return invoices
        
        # 按购方和销方税号分组
        invoice_groups = {}
        for i, invoice in enumerate(invoices):
            # 创建分组键：购方税号 + 销方税号
            customer_tax_no = invoice.customer.tax_no or ""
            supplier_tax_no = invoice.supplier.tax_no or ""
            group_key = f"{customer_tax_no}_{supplier_tax_no}"
            
            print(f"发票 {i+1}:")
            print(f"  发票号: {getattr(invoice, 'invoice_number', 'N/A')}")
            print(f"  购方税号: '{customer_tax_no}'")
            print(f"  销方税号: '{supplier_tax_no}'")
            print(f"  分组键: '{group_key}'")
            print(f"  明细行数量: {len(invoice.items)}")
            for j, item in enumerate(invoice.items):
                print(f"    明细 {j+1}: {item.name}, 数量: {item.quantity}, 金额: {item.amount}")
            
            if group_key not in invoice_groups:
                invoice_groups[group_key] = []
                print(f"  创建新分组: {group_key}")
            else:
                print(f"  加入现有分组: {group_key}")
            invoice_groups[group_key].append(invoice)
        
        print(f"\n分组结果:")
        for group_key, group_invoices in invoice_groups.items():
            print(f"  分组 '{group_key}': {len(group_invoices)} 张发票")
        
        merged_invoices = []
        for group_key, group_invoices in invoice_groups.items():
            print(f"\n处理分组 '{group_key}' ({len(group_invoices)} 张发票):")
            
            if len(group_invoices) == 1:
                # 只有一张发票，不需要合并
                print(f"  只有1张发票，无需合并")
                merged_invoices.append(group_invoices[0])
                continue
            
            print(f"  需要合并 {len(group_invoices)} 张发票")
            # 合并同组的发票
            merged_invoice = self._merge_invoice_group(group_invoices)
            merged_invoices.append(merged_invoice)
            
            print(f"  合并完成，合并后明细行数量: {len(merged_invoice.items)}")
            
            self._add_log("INFO", "merge_group_result", f"合并了{len(group_invoices)}张发票到一张发票，分组键: {group_key}", {
                "group_key": group_key,
                "input_count": len(group_invoices),
                "output_count": 1
            })
        
        print(f"\n=== 合并完成 ===")
        print(f"原始发票数量: {len(invoices)}")
        print(f"合并后发票数量: {len(merged_invoices)}")
        
        logger.debug(f"按购方销方税号合并完成，原{len(invoices)}张发票合并为{len(merged_invoices)}张\n\n")
        return merged_invoices
    
    def _merge_custom(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """自定义合并策略"""
        config = config or {}
        self._add_log("INFO", "merge_strategy_execution", f"执行自定义合并策略，配置: {config}", {
            "strategy": "custom",
            "input_count": len(invoices),
            "config": config
        })
        logger.debug(f"执行自定义合并策略，配置: {config}")
        
        # TODO: 实现自定义合并逻辑
        # 根据config中的规则进行合并
        
        self._add_log("WARN", "merge_strategy_not_implemented", "自定义合并功能暂未实现，返回原始发票列表", {
            "strategy": "custom"
        })
        return invoices
    
    def _merge_invoice_group(self, invoices: List[InvoiceDomainObject]) -> InvoiceDomainObject:
        """合并同组的发票（购方销方税号相同）
        
        Args:
            invoices: 同组的发票列表
            
        Returns:
            合并后的发票
        """
        print(f"\n  --- 开始合并发票组 ---")
        print(f"  待合并发票数量: {len(invoices)}")
        
        if not invoices:
            raise ValueError("发票列表不能为空")
        
        if len(invoices) == 1:
            print(f"  只有1张发票，直接返回")
            return invoices[0]
        
        # 使用第一张发票作为基础模板
        base_invoice = invoices[0]
        print(f"  使用发票 '{getattr(base_invoice, 'invoice_number', 'N/A')}' 作为基础模板")
        
        # 合并所有发票的明细行
        all_items = []
        total_original_items = 0
        for i, invoice in enumerate(invoices):
            print(f"  发票 {i+1} ({getattr(invoice, 'invoice_number', 'N/A')}): {len(invoice.items)} 个明细行")
            all_items.extend(invoice.items)
            total_original_items += len(invoice.items)
        
        print(f"  合并前总明细行数: {total_original_items}")
        print(f"  all_items 长度: {len(all_items)}")
        
        # 按税率、名称、税种合并明细行
        merged_items = self._merge_invoice_items(all_items)
        print(f"  合并后明细行数: {len(merged_items)}")
        
        # 重新计算总金额
        total_amount = sum(item.amount for item in merged_items)
        total_tax_amount = sum(item.tax_amount or 0 for item in merged_items)
        
        print(f"  重新计算金额:")
        print(f"    总金额: {total_amount}")
        print(f"    总税额: {total_tax_amount}")
        
        # 创建合并后的发票
        from copy import deepcopy
        merged_invoice = deepcopy(base_invoice)
        merged_invoice.items = merged_items
        merged_invoice.total_amount = total_amount
        merged_invoice.tax_amount = total_tax_amount
        merged_invoice.net_amount = total_amount - total_tax_amount
        
        # 更新发票号码（可以使用第一张发票的号码或生成新的）
        merged_invoice.invoice_number = f"MERGED_{base_invoice.invoice_number}"
        
        print(f"  合并后发票号: {merged_invoice.invoice_number}")
        print(f"  --- 发票组合并完成 ---")
        
        return merged_invoice
    
    def _merge_invoice_items(self, items: List) -> List:
        """合并发票明细行
        
        按税率、名称、税种合并明细行，相同的行合并数量和金额
        
        Args:
            items: 发票明细行列表
            
        Returns:
            合并后的明细行列表
        """
        from ..models.domain import InvoiceItem
        from decimal import Decimal
        
        print(f"\n    === 开始合并明细行 ===")
        print(f"    输入明细行数量: {len(items)}")
        
        # 按合并键分组
        item_groups = {}
        for i, item in enumerate(items):
            # 创建合并键：税率 + 名称 + 税种
            tax_rate = str(item.tax_rate or "")
            name = item.name or ""
            tax_category = item.tax_category or ""
            merge_key = f"{tax_rate}_{name}_{tax_category}"
            
            print(f"    明细行 {i+1}:")
            print(f"      名称: '{name}'")
            print(f"      税率: '{tax_rate}'")
            print(f"      税种: '{tax_category}'")
            print(f"      合并键: '{merge_key}'")
            print(f"      数量: {item.quantity}, 金额: {item.amount}")
            
            if merge_key not in item_groups:
                item_groups[merge_key] = []
                print(f"      创建新明细组: {merge_key}")
            else:
                print(f"      加入现有明细组: {merge_key}")
            item_groups[merge_key].append(item)
        
        print(f"\n    明细行分组结果:")
        for merge_key, group_items in item_groups.items():
            print(f"      分组 '{merge_key}': {len(group_items)} 个明细行")
        
        merged_items = []
        for merge_key, group_items in item_groups.items():
            print(f"\n    处理明细组 '{merge_key}' ({len(group_items)} 个明细行):")
            
            if len(group_items) == 1:
                # 只有一个明细行，不需要合并
                print(f"      只有1个明细行，无需合并")
                merged_items.append(group_items[0])
                continue
            
            print(f"      需要合并 {len(group_items)} 个明细行")
            # 合并同组的明细行
            base_item = group_items[0]
            
            # 累加数量、金额、税额
            total_quantity = sum(item.quantity for item in group_items)
            total_amount = sum(item.amount for item in group_items)
            total_tax_amount = sum(item.tax_amount or Decimal('0') for item in group_items)
            
            print(f"      合并前明细:")
            for j, item in enumerate(group_items):
                print(f"        明细 {j+1}: 数量 {item.quantity}, 金额 {item.amount}, 税额 {item.tax_amount or 0}")
            
            print(f"      合并后汇总:")
            print(f"        总数量: {total_quantity}")
            print(f"        总金额: {total_amount}")
            print(f"        总税额: {total_tax_amount}")
            
            # 重新计算单价（总金额/总数量）
            unit_price = total_amount / total_quantity if total_quantity > 0 else Decimal('0')
            print(f"        重新计算单价: {unit_price}")
            
            # 创建合并后的明细行
            from copy import deepcopy
            merged_item = deepcopy(base_item)
            merged_item.quantity = total_quantity
            merged_item.amount = total_amount
            merged_item.tax_amount = total_tax_amount
            merged_item.unit_price = unit_price
            
            # 更新描述，标明这是合并的明细
            merged_item.description = f"合并明细: {base_item.description}"
            
            merged_items.append(merged_item)
            print(f"      明细组合并完成")
        
        print(f"\n    === 明细行合并完成 ===")
        print(f"    原始明细行数量: {len(items)}")
        print(f"    合并后明细行数量: {len(merged_items)}")
        
        return merged_items
    
    def get_execution_log(self) -> List[str]:
        """获取执行日志"""
        return self.execution_log.copy()
    
    def get_merge_summary(self, original_count: int, processed_count: int) -> Dict[str, Any]:
        """获取合并摘要信息"""
        return {
            "original_invoice_count": original_count,
            "processed_invoice_count": processed_count,
            "merge_ratio": f"{processed_count}/{original_count}",
            "execution_log": self.execution_log
        }