# 数据库查询语法功能

## 概述

本项目实现了在CEL表达式中使用数据库查询语法的功能，允许在业务规则表达式中直接查询数据库数据。

## 语法格式

```
db.{table_name}.{field_name}[{condition_field}={condition_value}]
```

### 参数说明

- `table_name`: 数据库表名
- `field_name`: 要查询的字段名
- `condition_field`: 查询条件字段
- `condition_value`: 查询条件值（可以是变量引用或字符串字面量）

## 支持的表

- `companies`: 公司信息表
  - 字段: `name`, `tax_number`, `phone`, `email`, `address`, `category`
- `tax_rates`: 税率表
  - 字段: `rate`, `category`

## 使用示例

### 1. 基本查询
```cel
db.companies.tax_number[name=invoice.supplier.name]
```
根据发票供应商名称查询公司税号。

### 2. 字符串字面量查询
```cel
db.companies.category[name="示例公司A"]
```
查询指定公司的类别。

### 3. 税率查询
```cel
db.tax_rates.rate[category="GENERAL"]
```
查询指定类别的税率。

### 4. 复杂表达式
```cel
db.companies.tax_number[name=invoice.supplier.name] == "123456789" && invoice.total_amount > 1000
```
结合数据库查询和业务逻辑的复杂表达式。

### 5. 字段存在性检查
```cel
has(invoice, 'supplier') && db.companies.category[name=invoice.supplier.name] == "GENERAL"
```
检查字段存在性并进行数据库查询。

## 实现原理

1. **预处理阶段**: 在CEL表达式求值前，系统会识别并替换数据库查询语法
2. **查询执行**: 使用JPA Repository执行实际的数据库查询
3. **结果替换**: 将查询结果替换到原表达式中
4. **表达式求值**: 使用CEL引擎对替换后的表达式进行求值

## 测试

运行以下命令执行测试：

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=CelExpressionEvaluatorTest

# 运行演示
mvn test -Dtest=DatabaseQueryDemoTest
```

## 核心类

- `CelExpressionEvaluator`: 主要的表达式求值器
- `CompanyRepository`: 公司数据查询接口
- `TaxRateRepository`: 税率数据查询接口
- `DatabaseQueryDemo`: 功能演示类