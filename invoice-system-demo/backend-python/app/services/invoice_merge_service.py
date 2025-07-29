"""发票合并拆分服务 - 业务流程编排"""
from typing import List, Dict, Any, Optional
from ..models.domain import InvoiceDomainObject
from ..core.invoice_merge_engine import InvoiceMergeEngine, MergeStrategy


class InvoiceMergeService:
    """发票合并拆分服务 - 业务流程编排"""
    
    def __init__(self):
        self.merge_engine = InvoiceMergeEngine()
    
    def merge_and_split_invoices(
        self, 
        invoices: List[InvoiceDomainObject], 
        strategy: MergeStrategy,
        merge_config: Optional[Dict[str, Any]] = None,
        split_config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """合并拆分发票（一体化处理）- 业务流程编排
        
        Args:
            invoices: 待处理的发票列表
            strategy: 合并策略
            merge_config: 合并配置参数
            split_config: 拆分配置参数
            
        Returns:
            合并拆分后的发票列表
        """
        # 委托给核心引擎执行
        return self.merge_engine.merge_and_split(
            invoices, strategy, merge_config, split_config
        )
    
    def merge_invoices(
        self, 
        invoices: List[InvoiceDomainObject], 
        strategy: MergeStrategy,
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """合并发票（保留兼容性）
        
        Args:
            invoices: 待合并的发票列表
            strategy: 合并策略
            config: 合并配置参数
            
        Returns:
            合并后的发票列表
        """
        return self.merge_engine.merge(invoices, strategy, config)
    
    def split_invoices(
        self, 
        invoices: List[InvoiceDomainObject],
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """拆分发票（明细拆分）（保留兼容性）
        
        Args:
            invoices: 待拆分的发票列表
            config: 拆分配置参数
            
        Returns:
            拆分后的发票列表
        """
        return self.merge_engine.split(invoices, config)
    
    def get_merge_summary(self, original_count: int, merged_count: int) -> Dict[str, Any]:
        """获取合并摘要信息"""
        return self.merge_engine.get_merge_summary(original_count, merged_count)
    
    def get_execution_log(self) -> List[str]:
        """获取执行日志"""
        return self.merge_engine.get_execution_log()