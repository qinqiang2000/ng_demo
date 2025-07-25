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
    BY_CUSTOMER = "by_customer"  # 按客户合并
    BY_SUPPLIER = "by_supplier"  # 按供应商合并
    BY_DATE = "by_date"  # 按日期合并
    BY_TAX_PARTY = "by_tax_party"  # 按购方销方税号合并（新增的业务逻辑）
    CUSTOM = "custom"  # 自定义合并规则


class InvoiceMergeEngine:
    """发票合并拆分引擎 - 核心算法实现"""
    
    def __init__(self):
        self.execution_log = []
    
    def _get_timestamp(self) -> str:
        """获取当前时间戳"""
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    def merge_and_split(
        self, 
        invoices: List[InvoiceDomainObject], 
        strategy: MergeStrategy,
        merge_config: Optional[Dict[str, Any]] = None,
        split_config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """合并拆分发票（一体化处理）
        
        Args:
            invoices: 待处理的发票列表
            strategy: 合并策略
            merge_config: 合并配置参数
            split_config: 拆分配置参数
            
        Returns:
            合并拆分后的发票列表
        """
        self.execution_log = []
        
        # 添加开始日志
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "merge_and_split_start",
            "message": f"开始执行合并拆分一体化处理",
            "details": {
                "input_count": len(invoices),
                "strategy": strategy.value,
                "merge_config": merge_config,
                "split_config": split_config
            }
        })
        
        logger.info(f"开始合并拆分处理，输入发票数量: {len(invoices)}, 策略: {strategy.value}")
        
        # 第一步：执行合并
        merged_invoices = self._execute_merge(invoices, strategy, merge_config)
        
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
                "strategy": strategy.value
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
        """合并发票（单独执行合并）
        
        Args:
            invoices: 待合并的发票列表
            strategy: 合并策略
            config: 合并配置参数
            
        Returns:
            合并后的发票列表
        """
        return self._execute_merge(invoices, strategy, config)
    
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
        strategy: MergeStrategy,
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """执行合并操作
        
        Args:
            invoices: 待合并的发票列表
            strategy: 合并策略
            config: 合并配置参数
            
        Returns:
            合并后的发票列表
        """
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "merge_start",
            "message": f"开始执行合并策略: {strategy.value}",
            "details": {
                "strategy": strategy.value,
                "input_count": len(invoices),
                "config": config
            }
        })
        
        logger.debug(f"执行合并策略: {strategy.value}, 发票数量: {len(invoices)}")
        
        # 根据策略执行不同的合并逻辑
        result_invoices = None
        if strategy == MergeStrategy.NONE:
            result_invoices = self._no_merge(invoices)
        elif strategy == MergeStrategy.BY_CUSTOMER:
            result_invoices = self._merge_by_customer(invoices, config)
        elif strategy == MergeStrategy.BY_SUPPLIER:
            result_invoices = self._merge_by_supplier(invoices, config)
        elif strategy == MergeStrategy.BY_DATE:
            result_invoices = self._merge_by_date(invoices, config)
        elif strategy == MergeStrategy.BY_TAX_PARTY:
            result_invoices = self._merge_by_tax_party(invoices, config)
        elif strategy == MergeStrategy.CUSTOM:
            result_invoices = self._merge_custom(invoices, config)
        else:
            logger.warning(f"未知的合并策略: {strategy}, 使用默认策略")
            result_invoices = self._no_merge(invoices)
        
        # 添加合并完成日志
        self.execution_log.append({
            "timestamp": self._get_timestamp(),
            "level": "INFO",
            "operation": "merge_complete",
            "message": f"合并策略执行完成: {strategy.value}",
            "details": {
                "strategy": strategy.value,
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
        self.execution_log.append("执行不合并策略，保持原始发票结构")
        logger.debug("执行不合并策略")
        return invoices
    
    def _merge_by_customer(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """按客户合并发票 - 实现购方销方税号相同的发票合并"""
        self.execution_log.append("执行按客户合并策略")
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
            
            self.execution_log.append(f"合并了{len(group_invoices)}张发票到一张发票，分组键: {group_key}")
        
        logger.debug(f"按客户合并完成，原{len(invoices)}张发票合并为{len(merged_invoices)}张")
        return merged_invoices
    
    def _merge_by_supplier(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """按供应商合并发票"""
        self.execution_log.append("执行按供应商合并策略")
        logger.debug("执行按供应商合并策略")
        
        # TODO: 实现按供应商合并逻辑
        
        self.execution_log.append("按供应商合并功能暂未实现，返回原始发票列表")
        return invoices
    
    def _merge_by_date(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """按日期合并发票"""
        self.execution_log.append("执行按日期合并策略")
        logger.debug("执行按日期合并策略")
        
        # TODO: 实现按日期合并逻辑
        
        self.execution_log.append("按日期合并功能暂未实现，返回原始发票列表")
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
        self.execution_log.append("执行按购方销方税号合并策略")
        logger.debug("执行按购方销方税号合并策略")
        
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
            
            self.execution_log.append(f"合并了{len(group_invoices)}张发票到一张发票，分组键: {group_key}")
        
        logger.debug(f"按购方销方税号合并完成，原{len(invoices)}张发票合并为{len(merged_invoices)}张")
        return merged_invoices
    
    def _merge_custom(
        self, 
        invoices: List[InvoiceDomainObject], 
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """自定义合并策略"""
        config = config or {}
        self.execution_log.append(f"执行自定义合并策略，配置: {config}")
        logger.debug(f"执行自定义合并策略，配置: {config}")
        
        # TODO: 实现自定义合并逻辑
        # 根据config中的规则进行合并
        
        self.execution_log.append("自定义合并功能暂未实现，返回原始发票列表")
        return invoices
    
    def _merge_invoice_group(self, invoices: List[InvoiceDomainObject]) -> InvoiceDomainObject:
        """合并同组的发票（购方销方税号相同）
        
        Args:
            invoices: 同组的发票列表
            
        Returns:
            合并后的发票
        """
        if not invoices:
            raise ValueError("发票列表不能为空")
        
        if len(invoices) == 1:
            return invoices[0]
        
        # 使用第一张发票作为基础模板
        base_invoice = invoices[0]
        
        # 合并所有发票的明细行
        all_items = []
        for invoice in invoices:
            all_items.extend(invoice.items)
        
        # 按税率、名称、税种合并明细行
        merged_items = self._merge_invoice_items(all_items)
        
        # 重新计算总金额
        total_amount = sum(item.amount for item in merged_items)
        total_tax_amount = sum(item.tax_amount or 0 for item in merged_items)
        
        # 创建合并后的发票
        from copy import deepcopy
        merged_invoice = deepcopy(base_invoice)
        merged_invoice.items = merged_items
        merged_invoice.total_amount = total_amount
        merged_invoice.tax_amount = total_tax_amount
        merged_invoice.net_amount = total_amount - total_tax_amount
        
        # 更新发票号码（可以使用第一张发票的号码或生成新的）
        merged_invoice.invoice_number = f"MERGED_{base_invoice.invoice_number}"
        
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
        
        # 按合并键分组
        item_groups = {}
        for item in items:
            # 创建合并键：税率 + 名称 + 税种
            tax_rate = str(item.tax_rate or "")
            name = item.name or ""
            tax_category = item.tax_category or ""
            merge_key = f"{tax_rate}_{name}_{tax_category}"
            
            if merge_key not in item_groups:
                item_groups[merge_key] = []
            item_groups[merge_key].append(item)
        
        merged_items = []
        for merge_key, group_items in item_groups.items():
            if len(group_items) == 1:
                # 只有一个明细行，不需要合并
                merged_items.append(group_items[0])
                continue
            
            # 合并同组的明细行
            base_item = group_items[0]
            
            # 累加数量、金额、税额
            total_quantity = sum(item.quantity for item in group_items)
            total_amount = sum(item.amount for item in group_items)
            total_tax_amount = sum(item.tax_amount or Decimal('0') for item in group_items)
            
            # 重新计算单价（总金额/总数量）
            unit_price = total_amount / total_quantity if total_quantity > 0 else Decimal('0')
            
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