# 数据库集成功能手工测试用例

## 测试前准备

1. 启动系统：
```bash
cd invoice-system-demo
./start.sh
```

2. 访问地址：
   - 前端界面: http://localhost:3000
   - 后端API: http://localhost:8000
   - API文档: http://localhost:8000/docs

## 测试用例 1: 数据管理功能测试

### 1.1 测试企业管理
1. 访问前端页面，点击"数据管理"标签页
2. 在"企业管理"选项卡中：
   - 点击"新增企业"按钮
   - 填写表单：
     - 企业名称: "阿里巴巴网络技术有限公司"
     - 税号: "913300007799999999"
     - 分类: "科技企业"
     - 状态: 启用
   - 点击"创建"
   - 验证企业出现在表格中

### 1.2 测试税率配置
1. 切换到"税率管理"选项卡：
   - 点击"新增税率配置"按钮
   - 填写表单：
     - 税率名称: "高新科技企业税率"
     - 税率: 0.03 (表示3%)
     - 分类: "科技企业"
     - 最小金额: 0
     - 最大金额: 100000
     - 状态: 启用
   - 点击"创建"
   - 验证税率配置出现在表格中

## 测试用例 2: 数据库查询规则测试

### 2.1 测试供应商税号自动补全
1. 回到"发票处理"页面
2. 输入以下测试XML（缺少供应商税号）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">
    <ID>INV-2024-TEST-001</ID>
    <IssueDate>2024-01-15</IssueDate>
    <InvoiceTypeCode>380</InvoiceTypeCode>
    <AccountingSupplierParty>
        <Party>
            <PartyName>
                <Name>携程广州</Name>
            </PartyName>
        </Party>
    </AccountingSupplierParty>
    <AccountingCustomerParty>
        <Party>
            <PartyName>
                <Name>金蝶广州</Name>
            </PartyName>
        </Party>
    </AccountingCustomerParty>
    <LegalMonetaryTotal>
        <LineExtensionAmount currencyID="CNY">1000.00</LineExtensionAmount>
        <TaxExclusiveAmount currencyID="CNY">943.40</TaxExclusiveAmount>
        <TaxInclusiveAmount currencyID="CNY">1000.00</TaxInclusiveAmount>
        <PayableAmount currencyID="CNY">1000.00</PayableAmount>
    </LegalMonetaryTotal>
</Invoice>
```

3. 点击"处理发票"
4. 预期结果：
   - 系统应自动补全供应商税号（从数据库查询）
   - 在"处理步骤"中看到相关日志
   - 在"处理结果"中看到补全的税号

### 2.2 测试智能税率计算
1. 使用上面的XML，但修改供应商名称为你在测试用例1.1中创建的企业名称
2. 点击"处理发票"
3. 预期结果：
   - 系统应根据企业分类从数据库查询税率
   - 自动计算税额（总金额 × 查询到的税率）
   - 在结果中看到正确的税额计算

## 测试用例 3: 数据库查询失败容错测试

### 3.1 测试未知企业的处理
1. 使用以下XML（包含数据库中不存在的企业）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">
    <ID>INV-2024-TEST-002</ID>
    <IssueDate>2024-01-15</IssueDate>
    <InvoiceTypeCode>380</InvoiceTypeCode>
    <AccountingSupplierParty>
        <Party>
            <PartyName>
                <Name>不存在的企业名称XYZ</Name>
            </PartyName>
        </Party>
    </AccountingCustomerParty>
    <AccountingCustomerParty>
        <Party>
            <PartyName>
                <Name>金蝶广州</Name>
            </PartyName>
        </Party>
    </AccountingCustomerParty>
    <LegalMonetaryTotal>
        <LineExtensionAmount currencyID="CNY">2000.00</LineExtensionAmount>
        <TaxExclusiveAmount currencyID="CNY">1886.79</TaxExclusiveAmount>
        <TaxInclusiveAmount currencyID="CNY">2000.00</TaxInclusiveAmount>
        <PayableAmount currencyID="CNY">2000.00</PayableAmount>
    </LegalMonetaryTotal>
</Invoice>
```

2. 点击"处理发票"
3. 预期结果：
   - 系统应使用默认值（税号为空，分类为GENERAL，税率为6%）
   - 不应报错，而是优雅降级处理

## 测试用例 4: 数据筛选和搜索功能

### 4.1 测试企业搜索
1. 在"数据管理" -> "企业管理"中
2. 在搜索框输入"携程"
3. 点击"搜索"
4. 预期结果：只显示名称包含"携程"的企业

### 4.2 测试税率分类筛选
1. 在"税率管理"中
2. 选择分类下拉框中的"科技企业"
3. 点击"筛选"
4. 预期结果：只显示分类为"科技企业"的税率配置

## 测试用例 5: 数据库CRUD操作测试

### 5.1 测试企业信息编辑
1. 在企业列表中找到任意一个企业
2. 点击"编辑"按钮
3. 修改企业信息（如地址、电话等）
4. 点击"更新"
5. 预期结果：
   - 显示更新成功消息
   - 表格中的信息已更新

### 5.2 测试企业删除
1. 在企业列表中选择一个测试企业
2. 点击"删除"按钮
3. 在确认对话框中点击"确定"
4. 预期结果：
   - 显示删除成功消息
   - 企业从列表中消失
   - 统计数据更新

## 测试用例 6: API接口直接测试

### 6.1 使用Swagger UI测试
1. 访问 http://localhost:8000/docs
2. 测试以下接口：
   - `GET /api/data/companies` - 获取企业列表
   - `POST /api/data/companies` - 创建企业
   - `GET /api/data/tax-rates` - 获取税率配置
   - `GET /api/data/stats` - 获取统计信息

### 6.2 测试参数验证
1. 在Swagger UI中测试创建企业接口
2. 提交空的企业名称
3. 预期结果：返回400错误和相应的错误信息

## 测试用例 7: 统计功能测试

### 7.1 验证统计数据准确性
1. 在"数据管理"页面查看顶部的统计卡片
2. 记录当前数字：企业总数、活跃企业、税率配置、活跃税率
3. 创建一个新企业
4. 刷新页面或点击"刷新数据"
5. 预期结果：统计数字应相应增加

## 常见问题排查

### 问题1: 数据库查询返回null
- 检查企业名称是否在数据库中存在
- 查看后端日志，确认查询SQL执行情况
- 验证rules.yaml中的查询函数名称是否正确

### 问题2: 前端显示"加载数据失败"
- 检查后端服务是否正常运行（访问 http://localhost:8000/docs）
- 查看浏览器控制台的网络请求错误
- 确认CORS配置是否正确

### 问题3: 税率计算不正确
- 验证税率配置的金额范围设置
- 检查企业分类是否匹配
- 查看rules.yaml中的税率计算逻辑

## 测试结果记录模板

| 测试用例 | 执行时间 | 预期结果 | 实际结果 | 状态 | 备注 |
|---------|---------|---------|---------|------|------|
| 1.1 企业管理 | | 企业创建成功 | | ✅/❌ | |
| 1.2 税率配置 | | 税率创建成功 | | ✅/❌ | |
| 2.1 税号补全 | | 自动补全税号 | | ✅/❌ | |
| 2.2 税率计算 | | 智能税率计算 | | ✅/❌ | |
| ... | | | | | |

使用这些测试用例可以全面验证数据库集成功能是否正常工作。建议按顺序执行，每个测试用例都验证通过后再进行下一个。