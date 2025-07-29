"""
Product API Service - 模拟外部API获取商品信息
提供商品名称、税率、税种等信息的查询服务
"""
from typing import Dict, Optional, Any
from decimal import Decimal

class ProductAPIService:
    """模拟外部API服务，提供商品信息查询"""
    
    # 关键词配置字典 - 方便修改和维护
    KEYWORD_CONFIG = {
        "住": {
            "standard_name": "住宿费",
            "tax_rate": Decimal("0.13"),
            "tax_category": "增值税专票",
            "category_code": "ACCOMMODATION"
        },
        "餐": {
            "standard_name": "餐饮",
            "tax_rate": Decimal("0.06"),
            "tax_category": "增值税普票",
            "category_code": "CATERING"
        },
        "停车": {
            "standard_name": "停车费",
            "tax_rate": Decimal("0.09"),
            "tax_category": "不动产租赁",
            "category_code": "PARKING"
        }
    }
    
    # 保留原有的商品数据库作为备用
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
        # 优先使用关键词配置进行匹配
        for keyword, info in cls.KEYWORD_CONFIG.items():
            if keyword in description:
                return {
                    "standard_name": info["standard_name"],
                    "tax_rate": float(info["tax_rate"]),
                    "tax_category": info["tax_category"],
                    "category_code": info["category_code"]
                }
        
        # 如果关键词配置没有匹配，使用原有的商品数据库
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
    
    @classmethod
    def add_keyword_config(cls, keyword: str, standard_name: str, tax_rate: float, tax_category: str, category_code: str = "CUSTOM"):
        """
        添加新的关键词配置
        
        Args:
            keyword: 关键词
            standard_name: 标准名称
            tax_rate: 税率
            tax_category: 税种
            category_code: 分类代码
        """
        cls.KEYWORD_CONFIG[keyword] = {
            "standard_name": standard_name,
            "tax_rate": Decimal(str(tax_rate)),
            "tax_category": tax_category,
            "category_code": category_code
        }
    
    @classmethod
    def update_keyword_config(cls, keyword: str, **kwargs):
        """
        更新现有关键词配置
        
        Args:
            keyword: 要更新的关键词
            **kwargs: 要更新的字段（standard_name, tax_rate, tax_category, category_code）
        """
        if keyword in cls.KEYWORD_CONFIG:
            for key, value in kwargs.items():
                if key in ["standard_name", "tax_category", "category_code"]:
                    cls.KEYWORD_CONFIG[keyword][key] = value
                elif key == "tax_rate":
                    cls.KEYWORD_CONFIG[keyword][key] = Decimal(str(value))
    
    @classmethod
    def remove_keyword_config(cls, keyword: str):
        """删除关键词配置"""
        if keyword in cls.KEYWORD_CONFIG:
            del cls.KEYWORD_CONFIG[keyword]
    
    @classmethod
    def get_keyword_configs(cls) -> Dict[str, Dict[str, Any]]:
        """获取所有关键词配置"""
        return cls.KEYWORD_CONFIG.copy()


# 创建单例实例
product_api = ProductAPIService()