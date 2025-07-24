"""发票合并拆分服务"""
from typing import List, Dict, Any, Optional
from ..models.domain import InvoiceDomainObject
from enum import Enum


class MergeStrategy(Enum):
    """合并策略枚举"""
    NONE = "none"  # 不合并，保持原样
    BY_CUSTOMER = "by_customer"  # 按客户合并
    BY_SUPPLIER = "by_supplier"  # 按供应商合并
    BY_DATE = "by_date"  # 按日期合并
    CUSTOM = "custom"  # 自定义合并规则


class InvoiceMergeService:
    """发票合并拆分服务"""
    
    def __init__(self):
        self.execution_log = []
    
    def merge_invoices(
        self, 
        completed_invoices: List[InvoiceDomainObject], 
        strategy: MergeStrategy = MergeStrategy.NONE,
        merge_config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """合并发票
        
        Args:
            completed_invoices: 已补全的发票列表
            strategy: 合并策略
            merge_config: 合并配置参数
            
        Returns:
            合并后的发票列表
        """
        self.execution_log = []
        
        if not completed_invoices:
            self.execution_log.append("输入发票列表为空，无需合并")
            return []
        
        self.execution_log.append(f"开始合并 {len(completed_invoices)} 张发票，策略: {strategy.value}")
        
        if strategy == MergeStrategy.NONE:
            return self._no_merge(completed_invoices)
        elif strategy == MergeStrategy.BY_CUSTOMER:
            return self._merge_by_customer(completed_invoices)
        elif strategy == MergeStrategy.BY_SUPPLIER:
            return self._merge_by_supplier(completed_invoices)
        elif strategy == MergeStrategy.BY_DATE:
            return self._merge_by_date(completed_invoices)
        elif strategy == MergeStrategy.CUSTOM:
            return self._merge_custom(completed_invoices, merge_config or {})
        else:
            self.execution_log.append(f"未知的合并策略: {strategy.value}，使用默认策略")
            return self._no_merge(completed_invoices)
    
    def _no_merge(self, invoices: List[InvoiceDomainObject]) -> List[InvoiceDomainObject]:
        """不合并策略 - 保持原样"""
        self.execution_log.append("执行不合并策略，保持原始发票结构")
        return invoices
    
    def _merge_by_customer(self, invoices: List[InvoiceDomainObject]) -> List[InvoiceDomainObject]:
        """按客户合并发票"""
        self.execution_log.append("执行按客户合并策略")
        
        # TODO: 实现按客户合并逻辑
        # 1. 按客户分组
        # 2. 合并同一客户的发票头信息
        # 3. 合并明细行
        # 4. 重新计算金额
        
        self.execution_log.append("按客户合并功能暂未实现，返回原始发票列表")
        return invoices
    
    def _merge_by_supplier(self, invoices: List[InvoiceDomainObject]) -> List[InvoiceDomainObject]:
        """按供应商合并发票"""
        self.execution_log.append("执行按供应商合并策略")
        
        # TODO: 实现按供应商合并逻辑
        
        self.execution_log.append("按供应商合并功能暂未实现，返回原始发票列表")
        return invoices
    
    def _merge_by_date(self, invoices: List[InvoiceDomainObject]) -> List[InvoiceDomainObject]:
        """按日期合并发票"""
        self.execution_log.append("执行按日期合并策略")
        
        # TODO: 实现按日期合并逻辑
        
        self.execution_log.append("按日期合并功能暂未实现，返回原始发票列表")
        return invoices
    
    def _merge_custom(self, invoices: List[InvoiceDomainObject], config: Dict[str, Any]) -> List[InvoiceDomainObject]:
        """自定义合并策略"""
        self.execution_log.append(f"执行自定义合并策略，配置: {config}")
        
        # TODO: 实现自定义合并逻辑
        # 根据config中的规则进行合并
        
        self.execution_log.append("自定义合并功能暂未实现，返回原始发票列表")
        return invoices
    
    def split_invoices(
        self, 
        merged_invoices: List[InvoiceDomainObject], 
        split_config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """拆分发票
        
        Args:
            merged_invoices: 合并后的发票列表
            split_config: 拆分配置参数
            
        Returns:
            拆分后的发票列表
        """
        self.execution_log.append(f"开始拆分 {len(merged_invoices)} 张发票")
        
        # TODO: 实现发票拆分逻辑
        # 1. 按明细行数量拆分
        # 2. 按金额阈值拆分
        # 3. 按业务规则拆分
        
        self.execution_log.append("发票拆分功能暂未实现，返回原始发票列表")
        return merged_invoices
    
    def get_merge_summary(self, original_count: int, merged_count: int) -> Dict[str, Any]:
        """获取合并摘要信息"""
        return {
            "original_invoice_count": original_count,
            "merged_invoice_count": merged_count,
            "merge_ratio": f"{merged_count}/{original_count}",
            "execution_log": self.execution_log
        }