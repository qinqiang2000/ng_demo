"""LLM集成服务 - 支持OpenAI和其他LLM提供商"""
import os
import json
from typing import Dict, Any, Optional, List
from pydantic import BaseModel
import httpx
from ..utils.logger import get_logger

logger = get_logger(__name__)


class LLMConfig(BaseModel):
    """LLM配置"""
    provider: str = "openai"  # openai, azure, anthropic等
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    model: str = "gpt-3.5-turbo"
    temperature: float = 0.1
    max_tokens: int = 2000


class RuleGenerationRequest(BaseModel):
    """规则生成请求"""
    description: str
    rule_type: str  # "completion" or "validation"
    context: Optional[str] = None
    examples: Optional[List[str]] = None


class LLMService:
    """LLM集成服务"""
    
    def __init__(self):
        self.config = self._load_config()
        self._setup_client()
    
    def _load_config(self) -> LLMConfig:
        """加载LLM配置"""
        config = LLMConfig()
        
        # 从环境变量读取配置
        config.provider = os.getenv("LLM_PROVIDER", "openai")
        config.api_key = os.getenv("OPENAI_API_KEY") or os.getenv("LLM_API_KEY")
        config.base_url = os.getenv("LLM_BASE_URL")
        config.model = os.getenv("LLM_MODEL", "gpt-3.5-turbo")
        config.temperature = float(os.getenv("LLM_TEMPERATURE", "0.1"))
        config.max_tokens = int(os.getenv("LLM_MAX_TOKENS", "2000"))
        
        return config
    
    def _setup_client(self):
        """设置HTTP客户端"""
        if not self.config.api_key:
            logger.warning("未配置LLM API密钥，LLM功能将不可用")
            self.client = None
            return
        
        # 设置请求头
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.config.api_key}"
        }
        
        # 设置基础URL
        if self.config.provider == "openai":
            base_url = self.config.base_url or "https://api.openai.com/v1"
        else:
            base_url = self.config.base_url or "https://api.openai.com/v1"
        
        self.client = httpx.AsyncClient(
            base_url=base_url,
            headers=headers,
            timeout=30.0
        )
    
    async def generate_rule(self, request: RuleGenerationRequest) -> Dict[str, Any]:
        """生成规则"""
        if not self.client:
            raise ValueError("LLM服务未配置或不可用")
        
        try:
            # 构建提示词
            prompt = self._build_prompt(request)
            
            # 调用LLM API
            response = await self._call_llm(prompt)
            
            # 解析结果
            rule_data = self._parse_response(response, request.rule_type)
            
            return {
                "success": True,
                "data": rule_data,
                "prompt_used": prompt[:200] + "..." if len(prompt) > 200 else prompt
            }
            
        except Exception as e:
            logger.error(f"LLM规则生成失败: {e}")
            return {
                "success": False,
                "error": str(e)
            }
    
    def _build_prompt(self, request: RuleGenerationRequest) -> str:
        """构建完整的提示词"""
        
        # 基础系统提示
        system_prompt = """你是一个发票处理规则配置专家。你需要根据用户的自然语言描述，生成符合系统要求的业务规则配置。

## 发票领域对象结构

发票对象(invoice)包含以下字段：
- invoice_number: 发票号码 (string)
- issue_date: 开票日期 (date)
- invoice_type: 发票类型 (string)
- country: 国家 (string)
- total_amount: 总金额 (decimal)
- tax_amount: 税额 (decimal)
- net_amount: 净额 (decimal)
- supplier: 供应商信息 (object)
  - name: 供应商名称 (string)
  - tax_no: 供应商税号 (string)
  - email: 供应商邮箱 (string)
  - phone: 供应商电话 (string)
- customer: 客户信息 (object)
  - name: 客户名称 (string)
  - tax_no: 客户税号 (string)
  - email: 客户邮箱 (string)
  - phone: 客户电话 (string)
- items: 发票项目列表 (array)
  - item_id: 项目ID (string)
  - description: 项目描述 (string)
  - name: 标准商品名称 (string)
  - quantity: 数量 (decimal)
  - unit_price: 单价 (decimal)
  - amount: 金额 (decimal)
  - tax_rate: 税率 (decimal)
  - tax_amount: 税额 (decimal)
- extensions: 扩展字段 (object)
  - supplier_category: 供应商分类 (string)
  - invoice_type: 发票类型 (string)
  - total_quantity: 总数量 (decimal)

## 表达式语法说明

支持的表达式语法：
1. CEL内置函数：
   - has(field): 检查字段是否存在且不为null
   - matches(pattern): 正则表达式匹配
   - size(): 获取数组或字符串长度
   - map(var, expr): 数组映射
   - filter(var, expr): 数组过滤
   - all(var, expr): 检查数组所有元素是否满足条件
   - exists(var, expr): 检查数组是否存在满足条件的元素

2. 自定义函数：
   - db_query('query_name', param1, param2, ...): 数据库查询
   - get_standard_name(description): 获取标准商品名称
   - get_tax_rate(description): 获取商品税率
   - get_tax_category(description): 获取商品税种

3. 操作符：==, !=, >, >=, <, <=, &&, ||, !, +, -, *, /, %

4. 特殊语法：
   - items[]: 表示对数组中每个元素应用规则
   - 字符串字面量用单引号: 'CN'
   - 数字直接写: 1000, 0.06

## 数据库查询函数

可用的数据库查询函数：
- get_tax_number_by_name: 根据公司名称查询税号
- get_company_category_by_name: 根据公司名称查询企业分类
- get_tax_rate_by_category_and_amount: 根据分类和金额查询税率

## 规则类型

### 补全规则 (completion)
用于自动填充缺失的字段值，包含：
- id: 规则唯一标识
- rule_name: 规则名称
- apply_to: 应用条件 (CEL表达式，空字符串表示无条件)
- target_field: 目标字段路径
- rule_expression: 计算表达式
- priority: 优先级 (数字越大优先级越高)
- active: 是否启用 (true/false)

### 校验规则 (validation)
用于验证字段是否符合业务要求，包含：
- id: 规则唯一标识
- rule_name: 规则名称
- apply_to: 应用条件 (CEL表达式，空字符串表示无条件)
- field_path: 校验字段路径
- rule_expression: 校验表达式 (返回boolean)
- error_message: 错误提示信息
- priority: 优先级 (数字越大优先级越高)
- active: 是否启用 (true/false)

## 示例

补全规则示例：
```yaml
- id: "completion_tax"
  rule_name: "计算税额"
  apply_to: "invoice.total_amount > 0 && !has(invoice.tax_amount)"
  target_field: "tax_amount"
  rule_expression: "invoice.total_amount * 0.06"
  priority: 90
  active: true
```

校验规则示例：
```yaml
- id: "validation_amount"
  rule_name: "总金额必须大于0"
  apply_to: ""
  field_path: "total_amount"
  rule_expression: "invoice.total_amount > 0"
  error_message: "发票总金额必须大于0"
  priority: 100
  active: true
```

请根据用户描述生成对应的规则配置，只返回JSON格式的规则对象，不要包含额外的解释。"""

        # 用户请求
        user_prompt = f"""
请为以下需求生成{request.rule_type}规则：

需求描述：{request.description}

{f"上下文信息：{request.context}" if request.context else ""}

{f"参考示例：{', '.join(request.examples)}" if request.examples else ""}

请生成符合系统格式的规则配置。
"""

        return f"{system_prompt}\n\n{user_prompt}"
    
    async def _call_llm(self, prompt: str) -> str:
        """调用LLM API"""
        if self.config.provider == "openai":
            return await self._call_openai(prompt)
        else:
            raise ValueError(f"不支持的LLM提供商: {self.config.provider}")
    
    async def _call_openai(self, prompt: str) -> str:
        """调用OpenAI API"""
        payload = {
            "model": self.config.model,
            "messages": [
                {"role": "user", "content": prompt}
            ],
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens
        }
        
        response = await self.client.post("/chat/completions", json=payload)
        response.raise_for_status()
        
        data = response.json()
        return data["choices"][0]["message"]["content"]
    
    def _parse_response(self, response: str, rule_type: str) -> Dict[str, Any]:
        """解析LLM响应"""
        try:
            # 尝试提取JSON内容
            response = response.strip()
            
            # 如果响应包含代码块，提取其中的JSON
            if "```json" in response:
                start = response.find("```json") + 7
                end = response.find("```", start)
                response = response[start:end].strip()
            elif "```" in response:
                start = response.find("```") + 3
                end = response.find("```", start)
                response = response[start:end].strip()
            
            # 解析JSON
            rule_data = json.loads(response)
            
            # 验证规则结构
            self._validate_rule_structure(rule_data, rule_type)
            
            return rule_data
            
        except json.JSONDecodeError as e:
            logger.error(f"JSON解析失败: {e}, 响应内容: {response}")
            raise ValueError(f"LLM返回的不是有效的JSON格式: {str(e)}")
        except Exception as e:
            logger.error(f"规则解析失败: {e}")
            raise ValueError(f"规则解析失败: {str(e)}")
    
    def _validate_rule_structure(self, rule_data: Dict[str, Any], rule_type: str):
        """验证规则结构"""
        required_fields = ["rule_name", "priority", "active"]
        
        if rule_type == "completion":
            required_fields.extend(["target_field", "rule_expression"])
        elif rule_type == "validation":
            required_fields.extend(["field_path", "rule_expression", "error_message"])
        
        missing_fields = [field for field in required_fields if field not in rule_data]
        if missing_fields:
            raise ValueError(f"缺少必需字段: {', '.join(missing_fields)}")
        
        # 设置默认值
        if "apply_to" not in rule_data:
            rule_data["apply_to"] = ""
        
        if "id" not in rule_data:
            import uuid
            rule_data["id"] = f"{rule_type}_{str(uuid.uuid4())[:8]}"
    
    async def close(self):
        """关闭HTTP客户端"""
        if self.client:
            await self.client.aclose()