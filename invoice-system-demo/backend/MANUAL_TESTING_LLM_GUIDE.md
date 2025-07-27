# LLM规则生成功能手工验收指南

## 概述

本指南帮助您验收LLM规则生成功能中的**Comprehensive Context Structure**实现，确保所有设计的功能都正确集成和工作。

## 验收范围

### 已实现的核心功能
1. **智能字段推断** - 从自然语言描述自动推断目标字段
2. **动态上下文生成** - 根据规则类型和目标字段生成相关上下文
3. **YAML模板系统** - 结构化的语法、领域模型、数据库、模式信息
4. **LLM集成** - OpenAI GPT-4o-mini API集成
5. **高质量规则生成** - 生成符合CEL语法和业务规范的规则

## 验收环境准备

### 1. 环境检查
```bash
cd invoice-system-demo/backend
source .venv/bin/activate
```

### 2. 配置验证
检查 `.env` 文件是否包含：
```bash
cat .env
```
应包含：
- `OPENAI_API_KEY=sk-proj-...`
- `LLM_MODEL=gpt-4.1-mini`
- `LLM_TEMPERATURE=0.0`
- `LLM_MAX_TOKENS=2000`

### 3. 依赖检查
```bash
pip list | grep openai
```
应显示：`openai x.x.x`

## 验收测试步骤

### 测试1: Comprehensive Context Structure演示

**执行命令：**
```bash
python demo_comprehensive_context.py
```

**验收要点：**

#### ✅ 智能字段推断验证
- [ ] 场景1："供应商税号" → 推断出 `tax_no`
- [ ] 场景2："邮箱格式" → 推断出校验字段（或显示"未推断出"）
- [ ] 场景3："税率计算" → 推断出 `tax_amount`

#### ✅ 动态上下文生成验证
查看每个场景的"生成的上下文统计"：
- [ ] `上下文类型`: 显示正确的RuleType
- [ ] `目标字段`: 显示推断的字段名
- [ ] `语法示例`: 数量 > 0
- [ ] `字段信息`: 某些场景显示✅
- [ ] `模式示例`: 数量 > 0

#### ✅ 高质量规则生成验证
检查"表达式质量分析"包含：
- [ ] ✅ 使用了null检查 (`has()` 函数)
- [ ] ✅ 使用了智能查询语法 (`db.table.field[conditions]`)
- [ ] ✅ 使用了正则表达式 (`.matches()`)
- [ ] ✅ 使用了条件表达式 (`? :`)

#### ✅ 综合优势确认
最后显示8个综合优势，包括：
- [ ] 🧠 智能字段推断
- [ ] 📊 动态上下文生成
- [ ] 🎯 Token优化
- [ ] 🔧 可扩展架构

**预期输出示例：**
```
🎯 Comprehensive Context Structure Demonstration
================================================================================
这个演示展示了comprehensive context structure相比硬编码提示词的优势...

📋 场景 1: 供应商税号补全
🔍 推断的目标字段: tax_no
📊 生成的上下文统计:
   • 上下文类型: RuleType.COMPLETION
   • 目标字段: tax_no
✅ 生成的规则:
   • 规则名称: 供应商税号空时从企业表查询补全
🎊 表达式质量分析:
   ✅ 使用了null检查
   ✅ 使用了智能查询语法
```

---

### 测试2: 完整集成测试

**执行命令：**
```bash
python test_llm_integration.py
```

**验收要点：**

#### ✅ LLM服务初始化
- [ ] 显示：`✅ LLM service initialized with model: gpt-4.1-mini`
- [ ] 显示：`API Key configured: ***xxxx`

#### ✅ 补全规则生成测试
- [ ] 显示：`✅ Completion rule generated successfully`
- [ ] 包含：`Target Field: tax_no`
- [ ] 包含：`Expression:` 开头的CEL表达式

#### ✅ 校验规则生成测试
- [ ] 显示：`✅ Validation rule generated successfully`
- [ ] 包含：`Field Path: supplier.tax_no`
- [ ] 包含：`Error Message:` 合理的错误消息

#### ✅ 集成服务测试
- [ ] 显示：`✅ Service completion rule generated`
- [ ] 显示：`Confidence: 90.0%`
- [ ] 显示：`✅ Service validation rule generated`

#### ✅ 复杂场景测试
验证3个复杂场景都成功：
- [ ] 场景1: 动态税率计算 - `✅ Generated`
- [ ] 场景2: 旅游服务发票项目校验 - `✅ Generated`
- [ ] 场景3: 大额发票客户税号必填 - `✅ Generated`

#### ✅ 最终结果
- [ ] 显示：`Overall: 3/3 tests passed`
- [ ] 显示：`🎉 All LLM integration tests passed!`

**预期输出示例：**
```
🚀 Starting LLM Integration Tests
✅ LLM service initialized with model: gpt-4.1-mini
✅ Completion rule generated successfully:
   Target Field: tax_no
   Expression: !has(invoice.supplier.tax_no)...

📊 Test Results Summary
LLM Service               ✅ PASSED
Rule Generation Service   ✅ PASSED
Complex Scenarios         ✅ PASSED
Overall: 3/3 tests passed
🎉 All LLM integration tests passed!
```

---

### 测试3: YAML模板文件验证

**执行命令：**
```bash
ls -la app/templates/rule_generation/
```

**验收要点：**
- [ ] 存在4个YAML文件：
  - [ ] `rule_syntax_reference.yaml`
  - [ ] `domain_model_reference.yaml` 
  - [ ] `database_schema_reference.yaml`
  - [ ] `rule_patterns.yaml`

**检查模板内容：**
```bash
head -20 app/templates/rule_generation/rule_syntax_reference.yaml
```

**验收要点：**
- [ ] 包含CEL语法示例
- [ ] 包含操作符说明
- [ ] 包含函数定义
- [ ] 包含智能查询语法

---

### 测试4: 核心服务文件验证

**检查LLM Context Service：**
```bash
ls -la app/services/llm_context_service.py
```

**检查集成状态：**
```bash
grep -n "llm_context_service" app/services/llm_service.py
```

**验收要点：**
- [ ] `llm_context_service.py` 文件存在
- [ ] `llm_service.py` 中有 `from ..services.llm_context_service import`
- [ ] `llm_service.py` 中有 `self.context_service = llm_context_service`

---

## 手工功能验收

### 测试5: 自定义场景测试

创建测试脚本验证自定义场景：

```bash
cat > manual_test_custom.py << 'EOF'
#!/usr/bin/env python3
import asyncio
from app.services.llm_service import LLMService, RuleGenerationRequest

async def test_custom_scenario():
    llm = LLMService()
    
    # 自定义测试场景
    request = RuleGenerationRequest(
        description="当客户地址为空时，根据客户名称查询默认地址",
        rule_type="completion"
    )
    
    result = await llm.generate_rule(request)
    
    if result["success"]:
        print("✅ 自定义场景测试成功")
        print(f"规则名称: {result['data']['rule_name']}")
        print(f"目标字段: {result['data'].get('target_field')}")
        print(f"表达式: {result['data']['rule_expression']}")
    else:
        print(f"❌ 测试失败: {result.get('error')}")

if __name__ == "__main__":
    asyncio.run(test_custom_scenario())
EOF

python manual_test_custom.py
```

**验收要点：**
- [ ] 显示：`✅ 自定义场景测试成功`
- [ ] 推断出合理的目标字段
- [ ] 生成符合CEL语法的表达式

---

## 故障排除

### 常见问题及解决方案

#### 1. OpenAI API错误
**错误信息：** `OpenAI API调用失败`
**解决方案：**
- 检查API Key是否正确
- 检查网络连接
- 验证API额度

#### 2. 模型不存在错误
**错误信息：** `model 'gpt-4.1-mini' not found`
**解决方案：**
- 修改`.env`中的`LLM_MODEL=gpt-4o-mini`

#### 3. Token限制错误
**错误信息：** `maximum context length exceeded`
**解决方案：**
- 检查`LLM_MAX_TOKENS=2000`设置

#### 4. 虚拟环境问题
**错误信息：** `ModuleNotFoundError`
**解决方案：**
```bash
source .venv/bin/activate
pip install -r requirements.txt
```

---

## 验收清单总结

### ✅ 功能完整性验证
- [ ] 智能字段推断功能正常
- [ ] 动态上下文生成功能正常
- [ ] YAML模板系统完整
- [ ] LLM API集成正常
- [ ] 规则生成质量符合预期

### ✅ 技术架构验证
- [ ] 4个YAML模板文件存在且内容完整
- [ ] LLM Context Service正确集成
- [ ] 字段推断映射表完整
- [ ] 错误处理机制完善

### ✅ 性能和质量验证
- [ ] 所有自动化测试通过
- [ ] 生成的规则包含null检查
- [ ] 生成的规则使用智能查询语法
- [ ] 表达式符合CEL规范
- [ ] 响应时间合理（< 10秒）

### ✅ 可扩展性验证
- [ ] 新增字段映射容易
- [ ] 新增YAML模板容易
- [ ] 新增规则模式容易
- [ ] 代码结构清晰可维护

---

## 验收结论

当所有上述测试项都通过时，可以确认：

**✅ Comprehensive Context Structure功能已完整实现并正确集成到LLM服务中**

该系统具备：
- 🧠 智能推断能力
- 📊 动态上下文适配
- 🎯 高效token使用
- 🔧 良好的可扩展性
- 📈 高质量规则生成
- 🔄 一致的输出格式

**系统已准备好用于生产环境！**