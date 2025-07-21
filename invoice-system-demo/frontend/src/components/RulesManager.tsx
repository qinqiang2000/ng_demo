import React, { useState, useEffect } from 'react';
import { Card, Tabs, List, Tag, Typography, Spin, Badge, Descriptions, Alert } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, EditOutlined } from '@ant-design/icons';
import { invoiceService } from '../services/api';

const { Title, Text } = Typography;

interface Rule {
  id: string;
  name: string;
  active: boolean;
  priority: number;
  apply_to: string;
  target_field?: string;
  field_path?: string;
  error_message?: string;
}

const RulesManager: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [completionRules, setCompletionRules] = useState<Rule[]>([]);
  const [validationRules, setValidationRules] = useState<Rule[]>([]);

  useEffect(() => {
    loadRules();
  }, []);

  const loadRules = async () => {
    setLoading(true);
    try {
      const response = await invoiceService.getRules();
      if (response.data.success) {
        setCompletionRules(response.data.data.completion_rules);
        setValidationRules(response.data.data.validation_rules);
      }
    } catch (error) {
      console.error('Failed to load rules:', error);
    } finally {
      setLoading(false);
    }
  };

  const renderCompletionRule = (rule: Rule) => (
    <Card 
      key={rule.id}
      className={`rule-item ${rule.active ? 'active' : 'inactive'}`}
      style={{ marginBottom: 16 }}
    >
      <Descriptions title={rule.name} size="small" column={1}>
        <Descriptions.Item label="ID">{rule.id}</Descriptions.Item>
        <Descriptions.Item label="状态">
          {rule.active ? (
            <Tag color="success" icon={<CheckCircleOutlined />}>启用</Tag>
          ) : (
            <Tag color="default" icon={<CloseCircleOutlined />}>禁用</Tag>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="优先级">
          <Badge count={rule.priority} style={{ backgroundColor: '#52c41a' }} />
        </Descriptions.Item>
        <Descriptions.Item label="应用条件">
          <Text code>{rule.apply_to || '无条件'}</Text>
        </Descriptions.Item>
        <Descriptions.Item label="目标字段">
          <Text code>{rule.target_field}</Text>
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );

  const renderValidationRule = (rule: Rule) => (
    <Card 
      key={rule.id}
      className={`rule-item ${rule.active ? 'active' : 'inactive'}`}
      style={{ marginBottom: 16 }}
    >
      <Descriptions title={rule.name} size="small" column={1}>
        <Descriptions.Item label="ID">{rule.id}</Descriptions.Item>
        <Descriptions.Item label="状态">
          {rule.active ? (
            <Tag color="success" icon={<CheckCircleOutlined />}>启用</Tag>
          ) : (
            <Tag color="default" icon={<CloseCircleOutlined />}>禁用</Tag>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="优先级">
          <Badge count={rule.priority} style={{ backgroundColor: '#1890ff' }} />
        </Descriptions.Item>
        <Descriptions.Item label="应用条件">
          <Text code>{rule.apply_to || '无条件'}</Text>
        </Descriptions.Item>
        <Descriptions.Item label="校验字段">
          <Text code>{rule.field_path}</Text>
        </Descriptions.Item>
        <Descriptions.Item label="错误信息">
          <Text type="danger">{rule.error_message}</Text>
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );

  return (
    <div>
      <Title level={3}>规则管理</Title>
      
      <Alert
        message="规则配置说明"
        description={
          <div>
            <p>1. 规则通过YAML文件配置，位于 backend/config/rules.yaml</p>
            <p>2. 补全规则：自动填充缺失的字段值</p>
            <p>3. 校验规则：验证字段是否符合业务要求</p>
            <p>4. 优先级数值越大，执行顺序越靠前</p>
          </div>
        }
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Spin spinning={loading}>
        <Tabs defaultActiveKey="1">
          <Tabs.TabPane tab={`补全规则 (${completionRules.length})`} key="1">
            {completionRules.map(renderCompletionRule)}
          </Tabs.TabPane>
          
          <Tabs.TabPane tab={`校验规则 (${validationRules.length})`} key="2">
            {validationRules.map(renderValidationRule)}
          </Tabs.TabPane>
        </Tabs>
      </Spin>
    </div>
  );
};

export default RulesManager;