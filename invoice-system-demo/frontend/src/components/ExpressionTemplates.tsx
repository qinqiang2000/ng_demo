import React from 'react';
import { Card, Button, Space, Typography, Tag, Collapse, message } from 'antd';
import { CopyOutlined, InfoCircleOutlined } from '@ant-design/icons';

const { Text, Paragraph } = Typography;
const { Panel } = Collapse;

interface Template {
  name: string;
  description: string;
  expression: string;
  category: string;
  ruleType: 'completion' | 'validation' | 'both';
  example?: string;
}

interface ExpressionTemplatesProps {
  onUseTemplate: (expression: string) => void;
  ruleType: 'completion' | 'validation';
}

const TEMPLATES: Template[] = [
  // 补全规则模板
  {
    name: '设置默认值',
    description: '为字段设置固定的默认值',
    expression: "'默认值'",
    category: '基础模板',
    ruleType: 'completion',
    example: "'CN' (设置国家为中国)"
  },
  {
    name: '税额计算',
    description: '根据总金额计算6%的税额',
    expression: 'invoice.total_amount * 0.06',
    category: '计算模板',
    ruleType: 'completion',
    example: '总金额1000元时，税额为60元'
  },
  {
    name: '净额计算',
    description: '总金额减去税额得到净额',
    expression: 'invoice.total_amount - invoice.tax_amount',
    category: '计算模板',
    ruleType: 'completion',
    example: '总金额1060元，税额60元，净额1000元'
  },
  {
    name: '数据库查询',
    description: '根据公司名称查询税号',
    expression: "db_query('get_tax_number_by_name', invoice.supplier.name)",
    category: '数据库查询',
    ruleType: 'completion',
    example: '查询"金蝶软件"的税号'
  },
  {
    name: '条件赋值',
    description: '根据条件设置不同的值',
    expression: 'invoice.total_amount > 1000 ? "大额订单" : "普通订单"',
    category: '条件逻辑',
    ruleType: 'completion',
    example: '金额大于1000时标记为大额订单'
  },
  {
    name: '字符串拼接',
    description: '拼接多个字段形成新的值',
    expression: 'invoice.supplier.name + "-" + invoice.invoice_number',
    category: '字符串处理',
    ruleType: 'completion',
    example: '"金蝶软件-INV001"'
  },

  // 校验规则模板
  {
    name: '必填字段检查',
    description: '检查字段是否存在且不为空',
    expression: 'has(invoice.supplier.name) && invoice.supplier.name != ""',
    category: '基础校验',
    ruleType: 'validation',
    example: '检查供应商名称是否填写'
  },
  {
    name: '数值范围检查',
    description: '检查数值是否在指定范围内',
    expression: 'invoice.total_amount > 0 && invoice.total_amount <= 999999',
    category: '数值校验',
    ruleType: 'validation',
    example: '检查总金额在0-999999之间'
  },
  {
    name: '格式验证',
    description: '使用正则表达式验证格式',
    expression: 'invoice.supplier.tax_no.matches("^[0-9]{15}[A-Z0-9]{3}$")',
    category: '格式校验',
    ruleType: 'validation',
    example: '验证税号格式：15位数字+3位字母数字'
  },
  {
    name: '条件校验',
    description: '在特定条件下进行校验',
    expression: 'invoice.total_amount <= 5000 || has(invoice.customer.tax_no)',
    category: '条件校验',
    ruleType: 'validation',
    example: '大额发票必须有客户税号'
  },
  {
    name: '数组元素检查',
    description: '检查数组中所有元素是否满足条件',
    expression: 'invoice.items.all(item, item.amount > 0 && item.quantity > 0)',
    category: '数组校验',
    ruleType: 'validation',
    example: '所有发票项目的金额和数量都必须大于0'
  },
  {
    name: '邮箱格式验证',
    description: '验证邮箱地址格式',
    expression: 'invoice.customer.email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$")',
    category: '格式校验',
    ruleType: 'validation',
    example: '验证客户邮箱格式'
  },

  // 通用模板
  {
    name: '字段存在检查',
    description: '检查字段是否存在',
    expression: 'has(invoice.tax_amount)',
    category: '基础检查',
    ruleType: 'both',
    example: '检查税额字段是否存在'
  },
  {
    name: '多条件组合',
    description: '使用AND/OR组合多个条件',
    expression: 'invoice.total_amount > 1000 && invoice.supplier.name != ""',
    category: '逻辑组合',
    ruleType: 'both',
    example: '总金额大于1000且供应商名称不为空'
  }
];

const ExpressionTemplates: React.FC<ExpressionTemplatesProps> = ({
  onUseTemplate,
  ruleType
}) => {
  const handleUseTemplate = (template: Template) => {
    onUseTemplate(template.expression);
    message.success(`已应用模板: ${template.name}`);
  };

  const handleCopyExpression = (expression: string, templateName: string) => {
    navigator.clipboard.writeText(expression);
    message.success(`已复制: ${templateName}`);
  };

  const filteredTemplates = TEMPLATES.filter(
    template => template.ruleType === ruleType || template.ruleType === 'both'
  );

  const groupedTemplates = filteredTemplates.reduce((groups, template) => {
    const category = template.category;
    if (!groups[category]) {
      groups[category] = [];
    }
    groups[category].push(template);
    return groups;
  }, {} as Record<string, Template[]>);

  const renderTemplate = (template: Template) => (
    <Card
      key={`${template.category}-${template.name}`}
      size="small"
      style={{ marginBottom: 8 }}
      actions={[
        <Button
          type="link"
          size="small"
          onClick={() => handleUseTemplate(template)}
        >
          使用模板
        </Button>,
        <Button
          type="link"
          size="small"
          icon={<CopyOutlined />}
          onClick={() => handleCopyExpression(template.expression, template.name)}
        >
          复制
        </Button>
      ]}
    >
      <div>
        <div style={{ marginBottom: 8 }}>
          <Text strong>{template.name}</Text>
          <Tag color="blue" style={{ marginLeft: 8 }}>
            {template.ruleType === 'both' ? '通用' : template.ruleType === 'completion' ? '补全' : '校验'}
          </Tag>
        </div>
        
        <Paragraph style={{ marginBottom: 8, fontSize: '12px', color: '#666' }}>
          {template.description}
        </Paragraph>
        
        <div style={{ marginBottom: 8 }}>
          <Text code style={{ fontSize: '11px', wordBreak: 'break-all' }}>
            {template.expression}
          </Text>
        </div>
        
        {template.example && (
          <div>
            <Text type="secondary" style={{ fontSize: '11px' }}>
              <InfoCircleOutlined style={{ marginRight: 4 }} />
              示例: {template.example}
            </Text>
          </div>
        )}
      </div>
    </Card>
  );

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Text strong>表达式模板库</Text>
        <br />
        <Text type="secondary" style={{ fontSize: '12px' }}>
          选择常用的表达式模板快速创建规则，或复制表达式进行修改
        </Text>
      </div>
      
      <Collapse size="small" ghost>
        {Object.entries(groupedTemplates).map(([category, templates]) => (
          <Panel 
            header={
              <span>
                {category} 
                <Tag color="default" style={{ marginLeft: 8 }}>
                  {templates.length}
                </Tag>
              </span>
            } 
            key={category}
          >
            <Space direction="vertical" style={{ width: '100%' }}>
              {templates.map(renderTemplate)}
            </Space>
          </Panel>
        ))}
      </Collapse>
    </div>
  );
};

export default ExpressionTemplates;