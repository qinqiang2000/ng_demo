"""
Product API Service - 模拟外部API获取商品信息
提供商品名称、税率、税种等信息的查询服务
"""
from typing import Dict, Optional, Any
from decimal import Decimal

class ProductAPIService:
    """模拟外部API服务，提供商品信息查询"""
    
    # 模拟的商品数据库
    PRODUCT_DATA = {
        "住房": {
            "standard_name": "住宿费",
            "tax_rate": Decimal("0.06"),
            "tax_category": "增值税",
            "category_code": "ACCOMMODATION"
        },
        "餐饮": {
            "standard_name": "餐费", 
            "tax_rate": Decimal("0.06"),
            "tax_category": "增值税",
            "category_code": "CATERING"
        },
        "交通": {
            "standard_name": "交通费",
            "tax_rate": Decimal("0.09"),
            "tax_category": "增值税",
            "category_code": "TRANSPORTATION"
        },
        "停车": {
            "standard_name": "停车费",
            "tax_rate": Decimal("0.06"),
            "tax_category": "增值税",
            "category_code": "PARKING"
        },
        "会议": {
            "standard_name": "会议费",
            "tax_rate": Decimal("0.06"),
            "tax_category": "增值税",
            "category_code": "CONFERENCE"
        },
        "培训": {
            "standard_name": "培训费",
            "tax_rate": Decimal("0.06"),
            "tax_category": "增值税",
            "category_code": "TRAINING"
        }
    }
    
    @classmethod
    def get_product_info(cls, description: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        根据描述获取商品信息
        
        Args:
            description: 商品描述
            context: 上下文信息（如供应商信息、发票类型等）
            
        Returns:
            包含standard_name、tax_rate、tax_category的字典
        """
        # 简单的关键词匹配逻辑
        description_lower = description.lower()
        
        for keyword, info in cls.PRODUCT_DATA.items():
            if keyword in description_lower:
                return {
                    "standard_name": info["standard_name"],
                    "tax_rate": float(info["tax_rate"]),
                    "tax_category": info["tax_category"],
                    "category_code": info["category_code"]
                }
        
        # 默认值
        return {
            "standard_name": description,  # 保持原描述
            "tax_rate": 0.06,  # 默认6%税率
            "tax_category": "增值税",
            "category_code": "OTHER"
        }
    
    @classmethod
    def get_standard_name(cls, description: str, context: Optional[Dict[str, Any]] = None) -> str:
        """获取标准商品名称"""
        info = cls.get_product_info(description, context)
        return info["standard_name"]
    
    @classmethod
    def get_tax_rate(cls, description: str, context: Optional[Dict[str, Any]] = None) -> float:
        """获取税率"""
        info = cls.get_product_info(description, context)
        return info["tax_rate"]
    
    @classmethod
    def get_tax_category(cls, description: str, context: Optional[Dict[str, Any]] = None) -> str:
        """获取税种"""
        info = cls.get_product_info(description, context)
        return info["tax_category"]


# 创建单例实例
product_api = ProductAPIService()