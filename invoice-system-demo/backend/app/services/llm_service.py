"""LLM集成服务 - 支持OpenAI和其他LLM提供商"""
import os
import json
from typing import Dict, Any, Optional, List
from pathlib import Path
from pydantic import BaseModel
from openai import AsyncOpenAI
from dotenv import load_dotenv
from ..utils.logger import get_logger
from ..services.llm_context_service import llm_context_service
from ..core.llm_rule_context import RuleType

# 加载.env文件
env_path = Path(__file__).parent.parent.parent / ".env"
if env_path.exists():
    load_dotenv(env_path)

logger = get_logger(__name__)


class LLMConfig(BaseModel):
    """LLM配置"""
    provider: str = "openai"  # openai, azure, anthropic等
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    model: str = "gpt-4.1-mini"
    temperature: float = 0.0
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
        self.context_service = llm_context_service
        self._setup_client()
    
    def _load_config(self) -> LLMConfig:
        """加载LLM配置"""
        config = LLMConfig()
        
        # 从环境变量读取配置
        config.provider = os.getenv("LLM_PROVIDER", "openai")
        config.api_key = os.getenv("OPENAI_API_KEY") or os.getenv("LLM_API_KEY")
        config.base_url = os.getenv("LLM_BASE_URL")
        config.model = os.getenv("LLM_MODEL", "gpt-4.1-mini")
        config.temperature = float(os.getenv("LLM_TEMPERATURE", "0.0"))
        config.max_tokens = int(os.getenv("LLM_MAX_TOKENS", "2000"))
        
        return config
    
    def _setup_client(self):
        """设置OpenAI客户端"""
        if not self.config.api_key:
            logger.warning("未配置LLM API密钥，LLM功能将不可用")
            self.client = None
            return
        
        try:
            # 使用OpenAI官方库
            client_kwargs = {
                "api_key": self.config.api_key,
                "timeout": 30.0,
                "max_retries": 3
            }
            
            if self.config.base_url:
                client_kwargs["base_url"] = self.config.base_url
            
            self.client = AsyncOpenAI(**client_kwargs)
            logger.info(f"OpenAI客户端初始化成功，模型: {self.config.model}")
            
        except Exception as e:
            logger.error(f"OpenAI客户端初始化失败: {e}")
            self.client = None
    
    async def generate_rule(self, request: RuleGenerationRequest) -> Dict[str, Any]:
        """生成规则"""
        if not self.client:
            raise ValueError("LLM服务未配置或不可用")
        
        try:
            logger.info("="*60)
            logger.info("🚀 开始LLM规则生成")
            logger.info(f"📝 用户需求: {request.description}")
            logger.info(f"🔧 规则类型: {request.rule_type}")
            
            # 构建提示词
            prompt = self._build_prompt(request)
            
            logger.info("="*60)
            logger.info("📤 最终发送给LLM的PROMPT:")
            logger.info("-"*40)
            logger.info(prompt)
            logger.info("-"*40)
            
            # 调用LLM API
            logger.info("🌐 调用LLM API...")
            response = await self._call_llm(prompt)
            
            logger.info("="*60)
            logger.info("📥 LLM原始响应:")
            logger.info("-"*40)
            logger.info(response)
            logger.info("-"*40)
            
            # 解析结果
            logger.info("🔍 解析LLM响应...")
            rule_data = self._parse_response(response, request.rule_type)
            
            logger.info("="*60)
            logger.info("✅ 解析后的规则数据:")
            logger.info("-"*40)
            logger.info(json.dumps(rule_data, indent=2, ensure_ascii=False))
            logger.info("-"*40)
            logger.info("🎉 LLM规则生成完成!")
            logger.info("="*60)
            
            return {
                "success": True,
                "data": rule_data,
                "prompt_used": prompt[:200] + "..." if len(prompt) > 200 else prompt,
                "llm_response": response,
                "debug_info": {
                    "target_field": self._infer_target_field(request.description) if request.rule_type == "completion" else self._infer_validation_field(request.description),
                    "prompt_length": len(prompt),
                    "response_length": len(response)
                }
            }
            
        except Exception as e:
            logger.error("="*60)
            logger.error("❌ LLM规则生成失败!")
            logger.error(f"🚨 错误信息: {e}")
            logger.error(f"🔍 错误类型: {type(e).__name__}")
            import traceback
            logger.error(f"📍 错误堆栈:\n{traceback.format_exc()}")
            logger.error("="*60)
            return {
                "success": False,
                "error": str(e)
            }
    
    def _build_prompt(self, request: RuleGenerationRequest) -> str:
        """构建完整的提示词 - 使用comprehensive context structure"""
        
        logger.info("🔧 开始构建LLM Prompt")
        
        # 转换rule_type为RuleType枚举
        rule_type = RuleType.COMPLETION if request.rule_type == "completion" else RuleType.VALIDATION
        logger.info(f"📋 规则类型转换: {request.rule_type} -> {rule_type}")
        
        # 确定目标字段
        target_field = None
        if rule_type == RuleType.COMPLETION:
            # 尝试从描述中推断目标字段
            target_field = self._infer_target_field(request.description)
            logger.info(f"🎯 推断的目标字段 (补全): {target_field}")
        elif rule_type == RuleType.VALIDATION:
            # 尝试从描述中推断校验字段
            target_field = self._infer_validation_field(request.description)
            logger.info(f"🎯 推断的校验字段 (校验): {target_field}")
        
        # 生成comprehensive context
        logger.info("🌐 生成comprehensive context...")
        context = self.context_service.generate_minimal_context(rule_type, target_field)
        logger.info(f"📊 Context包含的键: {list(context.keys())}")
        
        if 'patterns' in context:
            logger.info(f"📝 找到相关模式: {len(context['patterns'])} 个")
        if 'database' in context:
            logger.info(f"🗄️ 数据库上下文: {len(context['database'].get('tables', {}))} 个表")
        if 'field_info' in context:
            logger.info(f"🏷️ 字段信息: {context['field_info'].get('type', 'unknown')} 类型")
        
        # 构建字段特定的提示  
        if rule_type == RuleType.COMPLETION:
            field_spec = '"target_field": "CEL格式目标字段路径(包含invoice前缀)",'
            additional_fields = ''
        else:  # VALIDATION
            field_spec = '"field_path": "CEL格式校验字段路径(包含invoice前缀)",'
            additional_fields = '"error_message": "错误消息",'
        
        # 构建系统提示
        context_json = json.dumps(context, indent=2, ensure_ascii=False)
        
        system_prompt = f"""# 发票处理规则生成专家

你是一个专业的业务规则生成专家，擅长为发票处理系统生成准确、高效的业务规则。

## 任务要求
根据用户的自然语言描述，生成一个符合系统规范的{rule_type.value}规则。

## 系统上下文
```json
{context_json}
```

## 生成要求

1. **表达式规范**:
   - 使用CEL表达式语法
   - 支持智能查询语法: db.table.field[conditions]
   - 确保语法正确，类型匹配

2. **补全规则要求** (completion):
   - **apply_to**: CEL表达式，定义何时应用此规则的条件（如：字段为空、null等触发条件）
   - **target_field**: CEL格式字段路径，包含invoice前缀（如：invoice.customer.email）
   - **rule_expression**: CEL表达式，定义如何计算字段值（可以是数据库查询、计算公式等）
   - 处理字段为空或null的情况
   - 提供合理的默认值或回退策略
   - 使用has()函数检查字段存在性

3. **校验规则要求** (validation):
   - **apply_to**: CEL表达式，定义何时应用此校验规则的条件
   - **field_path**: CEL格式字段路径，包含invoice前缀（如：invoice.supplier.tax_no）
   - **rule_expression**: CEL表达式，定义校验逻辑，返回boolean值
   - 提供清晰的错误消息
   - 考虑边界情况

4. **数据库查询**:
   - 使用available tables: companies, tax_rates, business_rules
   - 处理查询失败的情况
   - 提供默认值

5. **统一CEL语法**:
   - **所有字段引用**: 统一使用CEL格式，包含invoice前缀
   - **apply_to**: CEL表达式，控制规则何时被触发执行
   - **target_field/field_path**: CEL格式字段路径，用于标识目标字段
   - **rule_expression**: CEL表达式，控制规则执行时的具体逻辑

## 输出格式
请严格按照以下JSON格式输出，不要包含任何其他内容:

```json
[
  {{
    "id": "generated_rule_id",
    "rule_name": "简洁的规则名称",
    "apply_to": "触发条件CEL表达式",
    {field_spec}
    "rule_expression": "具体执行逻辑CEL表达式",
    {additional_fields}
    "priority": 90,
    "active": true
  }}
]
```

## 示例说明
- 对于"当customer的邮箱为空时补全邮箱"：
  - apply_to: "!has(invoice.customer.email) || invoice.customer.email == null || invoice.customer.email == ''"
  - target_field: "invoice.customer.email"
  - rule_expression: "db.companies.email[name=invoice.customer.name]"
- 对于"当金额大于5000时校验税号"：
  - apply_to: "invoice.total_amount > 5000"
  - field_path: "invoice.supplier.tax_no"  
  - rule_expression: "has(invoice.supplier.tax_no) && invoice.supplier.tax_no != ''"
"""

        # 用户请求
        user_prompt = f"""
## 用户需求
{request.description}

{f"## 上下文信息\n{request.context}\n" if request.context else ""}

{f"## 参考示例\n{chr(10).join(f'- {ex}' for ex in request.examples)}\n" if request.examples else ""}

请根据以上需求和系统上下文生成规则。
"""

        return f"{system_prompt}\n\n{user_prompt}"
    
    def _infer_target_field(self, description: str) -> Optional[str]:
        """从描述中推断目标字段"""
        description_lower = description.lower()
        
        # 字段映射表
        field_keywords = {
            'tax_no': ['税号', 'tax_no', 'tax_number'],
            'tax_amount': ['税额', 'tax_amount'],
            'net_amount': ['净额', 'net_amount'],
            'supplier.tax_no': ['供应商税号', 'supplier tax'],
            'customer.tax_no': ['客户税号', 'customer tax'],
            'supplier.email': ['供应商邮箱', 'supplier email'],
            'customer.email': ['客户邮箱', 'customer email'],
            'extensions.supplier_category': ['供应商分类', 'supplier category', '行业分类'],
            'country': ['国家', 'country'],
            'total_amount': ['总金额', 'total_amount']
        }
        
        for field, keywords in field_keywords.items():
            if any(keyword in description_lower for keyword in keywords):
                return field
        
        return None
    
    def _infer_validation_field(self, description: str) -> Optional[str]:
        """从描述中推断校验字段"""
        description_lower = description.lower()
        
        # 校验字段映射表
        field_keywords = {
            'total_amount': ['总金额', 'total_amount', '金额'],
            'invoice_number': ['发票号码', 'invoice_number'],
            'supplier.name': ['供应商名称', 'supplier name'],
            'customer.name': ['客户名称', 'customer name'],
            'supplier.tax_no': ['供应商税号', 'supplier tax'],
            'customer.tax_no': ['客户税号', 'customer tax'],
            'items': ['项目', 'items', '明细']
        }
        
        for field, keywords in field_keywords.items():
            if any(keyword in description_lower for keyword in keywords):
                return field
        
        return None
    
    async def _call_llm(self, prompt: str) -> str:
        """调用LLM API"""
        if self.config.provider == "openai":
            return await self._call_openai(prompt)
        else:
            raise ValueError(f"不支持的LLM提供商: {self.config.provider}")
    
    async def _call_openai(self, prompt: str) -> str:
        """调用OpenAI API"""
        try:
            response = await self.client.chat.completions.create(
                model=self.config.model,
                messages=[
                    {"role": "user", "content": prompt}
                ],
                temperature=self.config.temperature,
                max_tokens=self.config.max_tokens
            )
            
            content = response.choices[0].message.content
            if not content:
                raise ValueError("OpenAI返回了空响应")
            
            return content
            
        except Exception as e:
            logger.error(f"OpenAI API调用失败: {e}")
            raise
    
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
                end = response.rfind("```")
                if end > start:
                    response = response[start:end].strip()
            
            # 解析JSON
            rule_data = json.loads(response)
            
            # 如果返回的是数组，取第一个元素
            if isinstance(rule_data, list) and len(rule_data) > 0:
                rule_data = rule_data[0]
            elif isinstance(rule_data, list) and len(rule_data) == 0:
                raise ValueError("LLM返回了空的规则数组")
            
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
        """关闭OpenAI客户端"""
        if self.client:
            await self.client.close()