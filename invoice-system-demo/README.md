# 下一代开票系统 MVP Demo

基于KDUBL和规则引擎的配置化开票系统演示

## 🚀 最新更新 (v0.19)

### 极简数据库查询语法
- **新语法**: `db.table.field[conditions]` 取代繁琐的 `db_query()` 函数
- **示例**: `db.companies.tax_number[name=invoice.supplier.name]`
- **优势**: 更直观、无需预定义查询模板、自动类型推断
- **详细语法**: 参见 [智能数据库查询语法手册](SMART_QUERY_SYNTAX.md)

### 完整的规则管理系统
- 可视化规则编辑器
- 规则热加载功能
- 表达式实时验证
- LLM 辅助规则生成

## 系统架构

本Demo实现了文档中描述的核心功能：

### 业务层
- **KDUBL处理引擎**：解析和生成KDUBL格式数据
- **Domain Object**：内存中的业务处理模型
- **业务规则引擎**：
  - 字段补全引擎（基于配置的自动补全）
  - 业务校验引擎（基于配置的业务规则校验）
- **业务连接器**：预留框架（本Demo假设UBL即为KDUBL）

### 通道层（Mock实现）
- 合规性校验（模拟返回）
- 发票交付（模拟返回）

### 配置管理
- YAML格式的规则配置文件
- 支持热更新（重启服务生效）

## 技术栈

- **后端**：Python + FastAPI + CEL-Python
- **前端**：React + TypeScript + Ant Design  
- **规则引擎**：Google CEL (Common Expression Language) 完整实现

## 快速开始

### 前置要求

- Python 3.8+
- Node.js 14+
- npm 6+

### 一键启动

```bash
./start.sh
```

启动后访问：
- 前端界面：http://localhost:3000
- 后端API：http://localhost:8000
- API文档：http://localhost:8000/docs

### 手动启动

1. **启动后端**
```bash
cd backend
pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```

2. **启动前端**
```bash
cd frontend
npm install
npm start
```

## 功能演示

### 1. 发票处理

- 支持上传XML文件或直接粘贴KDUBL内容
- 自动执行数据补全和业务校验
- 显示处理步骤和结果
- 模拟合规性校验

### 2. 规则管理

查看当前加载的规则配置：
- 补全规则：自动填充缺失字段
- 校验规则：验证业务逻辑

### 3. 测试数据

系统提供了示例发票数据：
- `invoice1.xml`：携程广州的多项费用报销
- `invoice2.xml`：简单的单项发票

## 规则配置

规则配置文件位于：`backend/config/rules.yaml`

### 补全规则示例

```yaml
field_completion_rules:
  - id: "completion_001"
    rule_name: "设置默认国家"
    apply_to: ""  # 无条件应用
    target_field: "country"
    rule_expression: "'CN'"
    priority: 100
    active: true
    
  - id: "completion_002"
    rule_name: "从数据库补全供应商税号"
    apply_to: "invoice.supplier.tax_no == null"
    target_field: "supplier.tax_no"
    rule_expression: "db.companies.tax_number[name=invoice.supplier.name]"  # 新语法
    priority: 100
    active: true
```

### 校验规则示例

```yaml
field_validation_rules:
  - id: "validation_001"
    rule_name: "发票号码必填"
    apply_to: ""
    field_path: "invoice_number"
    rule_expression: "has(invoice.invoice_number) AND invoice.invoice_number != ''"
    error_message: "发票号码不能为空"
    priority: 100
    active: true
```

## 添加新规则

1. 编辑 `backend/config/rules.yaml`
2. 添加新的补全或校验规则
3. 重启后端服务使规则生效

### 示例：添加新的补全规则

```yaml
- id: "completion_new"
  rule_name: "补全特定客户的地址"
  apply_to: "invoice.customer.name == '某公司' AND !has(invoice.customer.address)"
  target_field: "customer.address"
  rule_expression: |
    {
      "city": "北京",
      "country": "CN"
    }
  priority: 50
  active: true
```

### 示例：添加新的校验规则

```yaml
- id: "validation_new"
  rule_name: "特定金额范围校验"
  apply_to: "invoice.total_amount > 5000 AND invoice.total_amount < 10000"
  field_path: "extensions.approval_required"
  rule_expression: "has(invoice.extensions.approval_required)"
  error_message: "5000-10000元的发票需要审批标记"
  priority: 60
  active: true
```

## CEL表达式引擎

系统现已集成Google CEL (Common Expression Language) 引擎，提供强大的表达式计算能力。

### CEL表达式特性

- **类型安全**：编译时类型检查，避免运行时错误
- **高性能**：微秒级表达式执行性能
- **安全沙箱**：用户代码安全执行，防止恶意操作
- **丰富函数库**：内置字符串、数学、逻辑、数组操作函数

### 支持的CEL语法

#### 基础操作
```cel
// 字段访问
invoice.supplier.name

// 比较操作
invoice.total_amount > 1000
invoice.customer.name == '金蝶广州'

// 逻辑操作
has(invoice.tax_amount) && invoice.tax_amount > 0
invoice.total_amount > 5000 || invoice.items.size() > 10

// 数学运算
invoice.total_amount * 0.06
invoice.total_amount - invoice.tax_amount
```

#### 数据库查询（新语法）
```cel
// 单条件查询
db.companies.tax_number[name=invoice.supplier.name]

// 多条件查询
db.tax_rates.rate[category=$category, min_amount<=$amount, max_amount>=$amount]

// 查询所有字段
db.companies[name='携程广州']

// 带默认值
db.companies.category[name=$name] or 'GENERAL'
```

> **详细语法说明**: 完整的数据库查询语法文档请参见 [智能数据库查询语法手册](SMART_QUERY_SYNTAX.md)

#### 字符串操作
```cel
// 包含检查
invoice.supplier.name.contains('携程')

// 正则匹配
invoice.supplier.tax_no.matches('^[0-9]{15}[A-Z0-9]{3}$')

// 字符串比较
invoice.invoice_type.startsWith('BULK')
```

#### 数组操作
```cel
// 数组大小
invoice.items.size() > 5

// 全部匹配
invoice.items.all(item, item.amount > 0)

// 存在匹配
invoice.items.exists(item, item.description.contains('住房'))

// 过滤操作
invoice.items.filter(item, item.amount > 100)
```

#### 条件表达式
```cel
// 三元操作
invoice.total_amount > 10000 ? 'HIGH_VALUE' : 'NORMAL'

// 空值检查
has(invoice.tax_amount) ? invoice.tax_amount : 0.0
```

### 高级规则示例

#### 智能分类规则
```yaml
- id: "completion_smart_category"
  rule_name: "智能供应商分类"
  apply_to: "invoice.supplier.name.contains('携程') || invoice.supplier.name.contains('旅游')"
  target_field: "extensions.supplier_category"
  rule_expression: "'TRAVEL_SERVICE'"
  priority: 60
  active: true
```

#### 复杂验证规则
```yaml
- id: "validation_travel_items"
  rule_name: "旅游服务项目校验"
  apply_to: "has(invoice.extensions.supplier_category) && invoice.extensions.supplier_category == 'TRAVEL_SERVICE'"
  field_path: "items"
  rule_expression: "invoice.items.all(item, item.description.matches('^(住房|早餐|晚餐|停车费|交通费).*'))"
  error_message: "旅游服务发票项目描述不规范"
  priority: 75
  active: true
```

#### 条件计算规则
```yaml
- id: "completion_dynamic_tax"
  rule_name: "动态税率计算"
  apply_to: "invoice.total_amount > 0"
  target_field: "tax_amount"
  rule_expression: "invoice.total_amount * (invoice.total_amount > 5000 ? 0.13 : 0.06)"
  priority: 80
  active: true
```

### CEL函数参考

| 函数 | 说明 | 示例 |
|------|------|------|
| `has(field)` | 检查字段是否存在 | `has(invoice.tax_amount)` |
| `contains(str)` | 字符串包含检查 | `name.contains('公司')` |
| `matches(regex)` | 正则表达式匹配 | `tax_no.matches('^[0-9]+$')` |
| `startsWith(str)` | 字符串前缀检查 | `code.startsWith('INV')` |
| `endsWith(str)` | 字符串后缀检查 | `email.endsWith('.com')` |
| `size()` | 获取数组/字符串长度 | `items.size()` |
| `all(var, expr)` | 数组全部匹配 | `items.all(i, i.amount > 0)` |
| `exists(var, expr)` | 数组存在匹配 | `items.exists(i, i.tax_rate > 0)` |
| `filter(var, expr)` | 数组过滤 | `items.filter(i, i.amount > 100)` |

### 迁移指南

从简化版表达式迁移到CEL：

| 简化版语法 | CEL语法 |
|------------|---------|
| `AND`, `OR` | `&&`, `\|\|` |
| `!has(field)` | `!has(field)` |
| `field == null` | `!has(field)` 或 `field == null` |
| 字符串匹配 | `field.matches('pattern')` |
| 数组操作 | `array.size()`, `array.all()` |

## 测试工具

运行测试脚本：

```bash
cd backend
python test_invoice.py
```

## 系统特点

1. **配置化**：业务规则通过YAML配置，无需修改代码
2. **可扩展**：预留了连接器框架，便于集成外部系统
3. **Google CEL引擎**：完整的CEL表达式支持，提供强大的规则表达能力
4. **智能规则处理**：支持复杂条件判断、字符串匹配、数组操作等
5. **模块化**：业务层与通道层分离，职责清晰

## 后续扩展建议

1. **数据库支持**：添加规则持久化存储
2. **规则编辑器**：提供可视化的规则编辑界面
3. **CEL扩展函数**：添加更多发票业务相关的自定义函数
4. **真实连接器**：实现与ERP、OA等系统的实际对接
5. **通道层实现**：对接真实的合规性校验服务