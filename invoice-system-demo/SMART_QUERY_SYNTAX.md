# 智能数据库查询语法手册

## 概述

本系统实现了一套极简的数据库查询语法，无需预定义查询模板，支持自动类型推断和多条件查询。

## 基础语法

```
db.table.field[conditions]
```

或查询所有字段：

```
db.table[conditions]
```

## 语法结构

### 1. 表名部分 (table)
- `companies` - 企业信息表
- `tax_rates` - 税率配置表 
- `business_rules` - 业务规则表

### 2. 字段部分 (field)
**企业表字段 (companies):**
- `name` - 企业名称
- `tax_number` - 税号
- `category` - 企业分类
- `address` - 地址
- `phone` - 电话
- `email` - 邮箱

**税率表字段 (tax_rates):**
- `rate` - 税率值
- `category` - 税率分类
- `min_amount` - 最小适用金额
- `max_amount` - 最大适用金额

### 3. 条件部分 (conditions)
支持多个条件，用逗号分隔：`condition1, condition2, condition3`

## 支持的操作符

| 操作符 | 说明 | 示例 |
|--------|------|------|
| `=` 或 `==` | 等于 | `name=invoice.supplier.name` |
| `!=` | 不等于 | `status!=inactive` |
| `>` | 大于 | `amount>1000` |
| `>=` | 大于等于 | `min_amount>=500` |
| `<` | 小于 | `amount<5000` |
| `<=` | 小于等于 | `max_amount<=10000` |
| `IN` | 包含于 | `category IN ['TECH','TRAVEL']` |
| `NOT IN` | 不包含于 | `status NOT IN ['deleted']` |
| `LIKE` | 模糊匹配 | `name LIKE '%公司%'` |
| `BETWEEN` | 范围查询 | `amount BETWEEN 1000 AND 5000` |

## 值类型支持

### 1. 字符串值
使用单引号或双引号：
```
name='携程广州'
name="金蝶软件"
```

### 2. 数值
直接使用数字：
```
amount>=1000
rate=0.06
```

### 3. 布尔值
```
is_active=true
is_deleted=false
```

### 4. 空值
```
description=null
phone=null
```

### 5. 数组值（用于IN操作）
```
category IN ['TECH','TRAVEL','GENERAL']
```

## 变量引用

### 1. 上下文变量引用
使用 `$` 前缀引用变量：
```
db.companies.tax_number[name=$supplier_name]
```

### 2. 对象路径引用
直接使用点号路径：
```
db.companies.tax_number[name=invoice.supplier.name]
db.tax_rates.rate[category=invoice.category]
```

## 查询示例

### 1. 单条件查询
```cel
# 根据企业名称查询税号
db.companies.tax_number[name=invoice.supplier.name]

# 根据分类查询税率
db.tax_rates.rate[category='GENERAL']
```

### 2. 多条件查询
```cel
# 根据分类和金额范围查询税率
db.tax_rates.rate[category=$category, min_amount<=$amount, max_amount>=$amount]

# 查询活跃状态的企业
db.companies.name[is_active=true, category='TECH']
```

### 3. 查询所有字段
```cel
# 查询企业的所有信息
db.companies[name='携程广州']

# 查询税率的所有配置
db.tax_rates[category='TRAVEL_SERVICE']
```

### 4. 使用变量
```cel
# 使用上下文变量
db.companies.category[name=$supplier_name]

# 使用对象路径
db.companies.email[name=invoice.customer.name]
```

### 5. 带默认值的查询
```cel
# 如果查询不到结果则使用默认值
db.companies.category[name=$name] or 'GENERAL'

# 查询税率，默认为6%
db.tax_rates.rate[category=$category] or 0.06
```

### 6. 复杂条件查询
```cel
# 范围查询
db.tax_rates[min_amount<=1000, max_amount>=5000]

# 模糊匹配
db.companies.tax_number[name LIKE '%携程%']

# 多值匹配
db.companies[category IN ['TECH','TRAVEL'], is_active=true]
```

## 实现细节

### 1. 解析器 (SmartQueryParser)
- 位置：`backend/app/core/smart_query.py`
- 功能：解析查询语法，支持复杂条件和嵌套表达式
- 正则表达式：`r'db\.(\w+)(?:\.(\w+))?\[(.*?)\]'`

### 2. 执行器 (SmartQueryExecutor) 
- 位置：`backend/app/core/smart_query.py`
- 功能：将解析结果转换为SQL查询并执行
- 支持参数化查询，防止SQL注入

### 3. CEL集成
- 位置：`backend/app/core/cel_evaluator.py`
- 功能：在CEL表达式中自动识别和处理智能查询语法
- 模式匹配：`r'db\.\w+(?:\.\w+)?\[[^\]]+\]'`

## 错误处理

### 1. 语法错误
```
无效的查询语法: db.invalid_syntax
```

### 2. 表不存在
系统会在TABLE_MAPPING中查找，不存在的表会报错

### 3. 字段不存在
系统会在FIELD_MAPPING中查找，不存在的字段会报错

### 4. 值解析错误
不正确的值格式会导致类型转换错误

## 性能优化

### 1. 查询限制
- 默认每个查询只返回1条记录 (`LIMIT 1`)
- 避免大结果集影响性能

### 2. 参数化查询
- 使用SQLAlchemy的参数化查询防止SQL注入
- 支持查询计划缓存

### 3. 类型推断
- 自动识别数值、字符串、布尔值类型
- 减少显式类型转换

## 注意事项

1. **安全性**：所有查询都是参数化的，防止SQL注入
2. **性能**：查询默认限制为1条记录，避免大数据量影响性能
3. **类型转换**：系统会自动进行类型推断和转换
4. **错误处理**：查询失败时返回null，不会中断整个规则执行
5. **上下文依赖**：变量引用需要确保上下文中存在对应的值

## 查询功能示例

| 功能 | 语法示例 |
|------|----------|
| 单条件查询 | `db.companies.tax_number[name=invoice.supplier.name]` |
| 多条件查询 | `db.tax_rates.rate[category=$category, min_amount<=$amount, max_amount>=$amount]` |
| 查询所有字段 | `db.companies[name='携程广州']` |
| 默认值处理 | `db.companies.category[name=$name] or 'GENERAL'` |

新语法的优势：
- 更直观易读
- 无需预定义查询模板
- 支持复杂条件组合
- 自动类型推断
- 更好的IDE支持