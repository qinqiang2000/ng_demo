"""
灵活的数据库查询处理器
支持通过配置文件定义查询，无需硬编码
"""
import yaml
import re
import json
import asyncio
from typing import Any, Dict, List, Optional, Union
from pathlib import Path
from datetime import datetime, timedelta
from collections import OrderedDict
import hashlib


class QueryCache:
    """简单的内存查询缓存"""
    def __init__(self):
        self.cache = OrderedDict()
        self.max_size = 1000
        
    def get(self, key: str) -> Optional[Any]:
        if key in self.cache:
            value, expiry = self.cache[key]
            if datetime.now() < expiry:
                # 移到末尾（LRU）
                self.cache.move_to_end(key)
                return value
            else:
                del self.cache[key]
        return None
        
    def set(self, key: str, value: Any, ttl: int):
        # 限制缓存大小
        if len(self.cache) >= self.max_size:
            self.cache.popitem(last=False)
            
        expiry = datetime.now() + timedelta(seconds=ttl)
        self.cache[key] = (value, expiry)


class FlexibleDatabaseQuery:
    """灵活的数据库查询处理器"""
    
    def __init__(self, config_path: str = "config/database_queries.yaml"):
        self.config_path = config_path
        self.config = self._load_config()
        self.cache = QueryCache()
        self.mock_data = self._init_mock_data()
        
    def _load_config(self) -> Dict:
        """加载查询配置"""
        config_file = Path(self.config_path)
        if not config_file.exists():
            # 如果配置文件不存在，返回默认配置
            return {
                "query_templates": {},
                "queries": {},
                "data_sources": {"default": {"type": "mock"}},
                "execution_strategy": {"enable_cache": True, "query_timeout": 1000}
            }
            
        with open(config_file, 'r', encoding='utf-8') as f:
            return yaml.safe_load(f)
            
    def _init_mock_data(self) -> Dict:
        """初始化模拟数据"""
        return {
            "companies": {
                "携程广州": {
                    "tax_number": "91440101234567890A", 
                    "taxnumber": "91440101234567890A",  # 兼容字段名
                    "category": "TRAVEL_SERVICE",
                    "industry": "旅游服务"
                },
                "金蝶广州": {
                    "tax_number": "91440101987654321B",
                    "category": "SOFTWARE",
                    "industry": "软件服务"
                },
                "测试公司": {
                    "tax_number": "91110108MA01W8XL6K",
                    "category": "GENERAL",
                    "industry": "一般企业"
                }
            },
            "tax_rates": [
                {"category": "TRAVEL_SERVICE", "min_amount": 0, "max_amount": 10000, "rate": 0.03},
                {"category": "TRAVEL_SERVICE", "min_amount": 10001, "max_amount": 999999, "rate": 0.06},
                {"category": "SOFTWARE", "min_amount": 0, "max_amount": 999999, "rate": 0.06},
                {"category": "GENERAL", "min_amount": 0, "max_amount": 999999, "rate": 0.13}
            ],
            "addresses": {
                "携程广州": {
                    "street": "天河区珠江新城",
                    "city": "广州",
                    "postal_code": "510000",
                    "country": "CN"
                }
            }
        }
        
    def _generate_cache_key(self, query_name: str, params: List[Any]) -> str:
        """生成缓存键"""
        # 转换参数为可序列化格式
        serializable_params = []
        for param in params:
            if hasattr(param, '__float__'):  # Decimal类型
                serializable_params.append(float(param))
            else:
                serializable_params.append(param)
        key_data = f"{query_name}:{json.dumps(serializable_params, sort_keys=True)}"
        return hashlib.md5(key_data.encode()).hexdigest()
        
    def _build_sql(self, template: Dict, parameters: Dict, params: List[Any]) -> str:
        """根据模板构建SQL"""
        sql_template = self.config["query_templates"][template]["sql_template"]
        
        # 替换模板中的占位符
        sql = sql_template
        for key, value in parameters.items():
            sql = sql.replace(f"{{{key}}}", value)
            
        # 替换参数占位符
        for i, param in enumerate(params):
            sql = sql.replace(f":param{i+1}", f"'{param}'")
            sql = sql.replace(":value", f"'{param}'")
            
        return sql
        
    def _execute_mock_query(self, query_config: Dict, params: List[Any]) -> Any:
        """执行模拟查询"""
        query_params = query_config.get("parameters", {})
        
        # 处理不同类型的查询
        if query_config["template"] == "single_field_lookup":
            table = query_params["table"]
            return_field = query_params["return_field"]
            
            if table == "companies" and len(params) > 0:
                company_name = params[0]
                if company_name in self.mock_data["companies"]:
                    company = self.mock_data["companies"][company_name]
                    if return_field == "*":
                        return company
                    # 尝试获取字段值，支持下划线和无下划线版本
                    value = company.get(return_field)
                    if value is None:
                        value = company.get(return_field.replace("_", ""))
                    return value
                    
        elif query_config["template"] == "multi_condition_lookup":
            if query_params["table"] == "tax_rates" and len(params) >= 2:
                category = params[0]
                amount = float(params[1])
                
                for rate_config in self.mock_data["tax_rates"]:
                    if (rate_config["category"] == category and 
                        rate_config["min_amount"] <= amount <= rate_config["max_amount"]):
                        return rate_config["rate"]
                        
        elif query_config["template"] == "join_query":
            if "addresses" in query_params.get("joins", "") and len(params) > 0:
                company_name = params[0]
                if company_name in self.mock_data["addresses"]:
                    return self.mock_data["addresses"][company_name]
                    
        # 返回默认值
        return query_config.get("fallback_value")
        
    async def execute_query(self, query_name: str, params: List[Any]) -> Any:
        """执行查询"""
        # 检查查询是否存在
        if query_name not in self.config["queries"]:
            raise ValueError(f"Query '{query_name}' not found in configuration")
            
        query_config = self.config["queries"][query_name]
        
        # 检查缓存
        if self.config["execution_strategy"]["enable_cache"]:
            cache_key = self._generate_cache_key(query_name, params)
            cached_value = self.cache.get(cache_key)
            if cached_value is not None:
                return cached_value
                
        # 执行查询
        try:
            # 这里使用模拟数据，实际应用中应该连接真实数据库
            result = self._execute_mock_query(query_config, params)
            
            # 缓存结果
            if self.config["execution_strategy"]["enable_cache"]:
                template = self.config["query_templates"].get(query_config["template"], {})
                ttl = template.get("cache_ttl", 300)
                self.cache.set(cache_key, result, ttl)
                
            return result
            
        except Exception as e:
            # 返回默认值
            return query_config.get("fallback_value")
            
    async def query(self, query_name: str, *args) -> Any:
        """异步查询接口"""
        # 将参数转换为列表
        params = list(args)
        return await self.execute_query(query_name, params)
        
    def query_sync(self, query_name: str, *args) -> Any:
        """同步查询接口 - 仅用于非异步环境"""
        params = list(args)
        return asyncio.run(self.execute_query(query_name, params))
            
    def get_available_queries(self) -> List[Dict[str, str]]:
        """获取所有可用的查询"""
        queries = []
        for name, config in self.config["queries"].items():
            queries.append({
                "name": name,
                "description": config.get("description", ""),
                "template": config.get("template", ""),
                "parameters": config.get("parameters", {})
            })
        return queries
        
    def reload_config(self):
        """重新加载配置"""
        self.config = self._load_config()
        self.cache = QueryCache()  # 清空缓存


# 创建全局实例
_query_handler = None

def get_query_handler() -> FlexibleDatabaseQuery:
    """获取查询处理器实例"""
    global _query_handler
    if _query_handler is None:
        _query_handler = FlexibleDatabaseQuery()
    return _query_handler
    
    
async def db_query(query_name: str, *args) -> Any:
    """
    通用数据库查询函数
    
    使用示例：
    - await db_query('get_tax_number_by_name', '携程广州')
    - await db_query('get_tax_rate_by_category_and_amount', 'TRAVEL_SERVICE', 10000)
    """
    handler = get_query_handler()
    return await handler.query(query_name, *args)