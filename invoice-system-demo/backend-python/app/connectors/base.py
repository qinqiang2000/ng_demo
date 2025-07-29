"""业务连接器基类和注册器"""
from abc import ABC, abstractmethod
from typing import Dict, Any, List


class BaseBusinessConnector(ABC):
    """业务连接器基类"""
    
    @property
    @abstractmethod
    def name(self) -> str:
        """连接器名称"""
        pass
    
    @property
    @abstractmethod
    def description(self) -> str:
        """连接器描述"""
        pass
    
    @abstractmethod
    def transform_to_kdubl(self, source_data: Dict[str, Any]) -> str:
        """将源数据转换为KDUBL格式"""
        pass
    
    @abstractmethod
    def validate_source_data(self, source_data: Dict[str, Any]) -> bool:
        """验证源数据格式"""
        pass


class MockERPConnector(BaseBusinessConnector):
    """模拟的ERP连接器"""
    
    @property
    def name(self) -> str:
        return "ERP_CONNECTOR"
    
    @property
    def description(self) -> str:
        return "标准ERP系统连接器（模拟）"
    
    def transform_to_kdubl(self, source_data: Dict[str, Any]) -> str:
        # 模拟实现 - 实际应该转换数据
        return "<Invoice>Mock KDUBL</Invoice>"
    
    def validate_source_data(self, source_data: Dict[str, Any]) -> bool:
        # 模拟验证
        return True


class MockOAConnector(BaseBusinessConnector):
    """模拟的OA连接器"""
    
    @property
    def name(self) -> str:
        return "OA_CONNECTOR"
    
    @property
    def description(self) -> str:
        return "OA系统费用报销连接器（模拟）"
    
    def transform_to_kdubl(self, source_data: Dict[str, Any]) -> str:
        # 模拟实现
        return "<Invoice>Mock KDUBL from OA</Invoice>"
    
    def validate_source_data(self, source_data: Dict[str, Any]) -> bool:
        # 模拟验证
        return True


class BusinessConnectorRegistry:
    """业务连接器注册表"""
    
    def __init__(self):
        self.connectors: Dict[str, BaseBusinessConnector] = {}
        self._register_default_connectors()
    
    def _register_default_connectors(self):
        """注册默认连接器"""
        self.register(MockERPConnector())
        self.register(MockOAConnector())
    
    def register(self, connector: BaseBusinessConnector):
        """注册连接器"""
        self.connectors[connector.name] = connector
    
    def get_connector(self, name: str) -> BaseBusinessConnector:
        """获取连接器"""
        if name not in self.connectors:
            raise ValueError(f"连接器 {name} 未注册")
        return self.connectors[name]
    
    def list_connectors(self) -> List[Dict[str, str]]:
        """列出所有连接器"""
        return [
            {
                "name": connector.name,
                "description": connector.description
            }
            for connector in self.connectors.values()
        ]