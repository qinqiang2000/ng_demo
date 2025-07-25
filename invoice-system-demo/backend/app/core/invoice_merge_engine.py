"""发票合并拆分引擎 - 核心算法实现"""
from typing import List, Dict, Any, Optional
from ..models.domain import InvoiceDomainObject
from enum import Enum
from ..utils.logger import get_logger

# 创建logger
logger = get_logger(__name__)


class MergeStrategy(Enum):
    """合并策略枚举"""
    NONE = "none"  # 不合并，保持原样
    BY_CUSTOMER = "by_customer"  # 按客户合并
    BY_SUPPLIER = "by_supplier"  # 按供应商合并
    BY_DATE = "by_date"  # 按日期合并
    CUSTOM = "custom"  # 自定义合并规则


class InvoiceMergeEngine:
    """发票合并拆分引擎 - 核心算法实现"""
    
    def __init__(self):
        self.execution_log = []
    
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
        self.execution_log.append(f"开始执行合并拆分一体化处理，策略: {strategy.value}")
        logger.info(f"开始合并拆分处理，输入发票数量: {len(invoices)}, 策略: {strategy.value}")
        
        # 第一步：执行合并
        merged_invoices = self._execute_merge(invoices, strategy, merge_config)
        
        # 第二步：执行拆分（处理尾差等情况）
        processed_invoices = self._execute_split(merged_invoices, split_config)
        
        self.execution_log.append(f"合并拆分处理完成，输入{len(invoices)}张，输出{len(processed_invoices)}张发票")
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
        self.execution_log.append(f"执行合并策略: {strategy.value}")
        logger.debug(f"执行合并策略: {strategy.value}, 发票数量: {len(invoices)}")
        
        # 根据策略执行不同的合并逻辑
        if strategy == MergeStrategy.NONE:
            return self._no_merge(invoices)
        elif strategy == MergeStrategy.BY_CUSTOMER:
            return self._merge_by_customer(invoices, config)
        elif strategy == MergeStrategy.BY_SUPPLIER:
            return self._merge_by_supplier(invoices, config)
        elif strategy == MergeStrategy.BY_DATE:
            return self._merge_by_date(invoices, config)
        elif strategy == MergeStrategy.CUSTOM:
            return self._merge_custom(invoices, config)
        else:
            logger.warning(f"未知的合并策略: {strategy}, 使用默认策略")
            return self._no_merge(invoices)
    
    def _execute_split(
        self, 
        invoices: List[InvoiceDomainObject],
        config: Optional[Dict[str, Any]] = None
    ) -> List[InvoiceDomainObject]:
        """执行拆分操作（明细拆分）
        
        Args:
            invoices: 待拆分的发票列表
            config: 拆分配置参数
            
        Returns:
            拆分后的发票列表
        """
        self.execution_log.append("执行明细拆分")
        logger.debug(f"执行明细拆分，发票数量: {len(invoices)}")
        
        # TODO: 实现具体的明细拆分逻辑
        # 1. 检查发票明细是否需要拆分
        # 2. 处理尾差问题
        # 3. 按业务规则拆分明细行
        # 4. 重新计算金额
        
        return invoices
    
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
        """按客户合并发票"""
        self.execution_log.append("执行按客户合并策略")
        logger.debug("执行按客户合并策略")
        
        # TODO: 实现按客户合并逻辑
        # 1. 按客户分组
        # 2. 合并同一客户的发票头信息
        # 3. 合并明细行
        # 4. 重新计算金额
        
        self.execution_log.append("按客户合并功能暂未实现，返回原始发票列表")
        return invoices
    
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