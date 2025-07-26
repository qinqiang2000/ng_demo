import React, { useState, useEffect } from 'react';
import { 
  Card, 
  Tabs, 
  List, 
  Tag, 
  Typography, 
  Spin, 
  Badge, 
  Descriptions, 
  Alert, 
  Button, 
  Space, 
  Popconfirm, 
  message,
  Tooltip,
  Switch
} from 'antd';
import { 
  CheckCircleOutlined, 
  CloseCircleOutlined, 
  EditOutlined, 
  DeleteOutlined, 
  PlusOutlined,
  ReloadOutlined,
  BulbOutlined
} from '@ant-design/icons';
import { invoiceService } from '../services/api';
import RuleEditor from './RuleEditor';
import { Rule, RuleFormData } from '../types/rules';

const { Title, Text } = Typography;

const RulesManager: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [completionRules, setCompletionRules] = useState<Rule[]>([]);
  const [validationRules, setValidationRules] = useState<Rule[]>([]);
  const [editorVisible, setEditorVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<RuleFormData | undefined>(undefined);
  const [editorMode, setEditorMode] = useState<'create' | 'edit'>('create');
  const [currentRuleType, setCurrentRuleType] = useState<'completion' | 'validation'>('completion');

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
      message.error('加载规则失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateRule = (ruleType: 'completion' | 'validation') => {
    setCurrentRuleType(ruleType);
    setEditorMode('create');
    setEditingRule(undefined);
    setEditorVisible(true);
  };

  const handleEditRule = (rule: Rule, ruleType: 'completion' | 'validation') => {
    setCurrentRuleType(ruleType);
    setEditorMode('edit');
    setEditingRule({
      id: rule.id,
      rule_name: rule.rule_name || rule.name || '',
      apply_to: rule.apply_to,
      target_field: rule.target_field,
      field_path: rule.field_path,
      rule_expression: rule.rule_expression || '',
      error_message: rule.error_message,
      priority: rule.priority,
      active: rule.active
    });
    setEditorVisible(true);
  };

  const handleSaveRule = async (ruleData: RuleFormData) => {
    try {
      if (editorMode === 'create') {
        // 创建新规则
        if (currentRuleType === 'completion') {
          await invoiceService.createCompletionRule(ruleData);
        } else {
          await invoiceService.createValidationRule(ruleData);
        }
        message.success('规则创建成功');
      } else {
        // 更新现有规则
        const ruleId = editingRule?.id;
        if (!ruleId) {
          throw new Error('规则ID不存在');
        }
        
        if (currentRuleType === 'completion') {
          await invoiceService.updateCompletionRule(ruleId, ruleData);
        } else {
          await invoiceService.updateValidationRule(ruleId, ruleData);
        }
        message.success('规则更新成功');
      }
      
      setEditorVisible(false);
      loadRules(); // 重新加载规则
    } catch (error) {
      console.error('保存规则失败:', error);
      message.error('保存规则失败');
    }
  };

  const handleDeleteRule = async (ruleId: string, ruleType: 'completion' | 'validation') => {
    try {
      if (ruleType === 'completion') {
        await invoiceService.deleteCompletionRule(ruleId);
      } else {
        await invoiceService.deleteValidationRule(ruleId);
      }
      message.success('规则删除成功');
      loadRules(); // 重新加载规则
    } catch (error) {
      console.error('删除规则失败:', error);
      message.error('删除规则失败');
    }
  };

  const handleToggleRule = async (ruleId: string, ruleType: 'completion' | 'validation', active: boolean) => {
    try {
      if (ruleType === 'completion') {
        await invoiceService.updateCompletionRule(ruleId, { active });
      } else {
        await invoiceService.updateValidationRule(ruleId, { active });
      }
      message.success(active ? '规则已启用' : '规则已禁用');
      loadRules(); // 重新加载规则
    } catch (error) {
      console.error('切换规则状态失败:', error);
      message.error('切换规则状态失败');
    }
  };

  const handleReloadRules = async () => {
    setLoading(true);
    try {
      await invoiceService.reloadRules();
      message.success('规则重新加载成功');
      loadRules();
    } catch (error) {
      console.error('重新加载规则失败:', error);
      message.error('重新加载规则失败');
    } finally {
      setLoading(false);
    }
  };

  const renderCompletionRule = (rule: Rule) => (
    <Card 
      key={rule.id}
      className={`rule-item ${rule.active ? 'active' : 'inactive'}`}
      style={{ marginBottom: 16 }}
      actions={[
        <Tooltip title="编辑规则">
          <Button 
            type="text" 
            icon={<EditOutlined />} 
            onClick={() => handleEditRule(rule, 'completion')}
          />
        </Tooltip>,
        <Tooltip title={rule.active ? '禁用规则' : '启用规则'}>
          <Switch
            checked={rule.active}
            size="small"
            onChange={(checked) => handleToggleRule(rule.id, 'completion', checked)}
          />
        </Tooltip>,
        <Popconfirm
          title="确定要删除这个规则吗？"
          onConfirm={() => handleDeleteRule(rule.id, 'completion')}
          okText="确定"
          cancelText="取消"
        >
          <Tooltip title="删除规则">
            <Button 
              type="text" 
              danger 
              icon={<DeleteOutlined />}
            />
          </Tooltip>
        </Popconfirm>
      ]}
    >
      <Descriptions title={rule.rule_name || rule.name} size="small" column={1}>
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
      actions={[
        <Tooltip title="编辑规则">
          <Button 
            type="text" 
            icon={<EditOutlined />} 
            onClick={() => handleEditRule(rule, 'validation')}
          />
        </Tooltip>,
        <Tooltip title={rule.active ? '禁用规则' : '启用规则'}>
          <Switch
            checked={rule.active}
            size="small"
            onChange={(checked) => handleToggleRule(rule.id, 'validation', checked)}
          />
        </Tooltip>,
        <Popconfirm
          title="确定要删除这个规则吗？"
          onConfirm={() => handleDeleteRule(rule.id, 'validation')}
          okText="确定"
          cancelText="取消"
        >
          <Tooltip title="删除规则">
            <Button 
              type="text" 
              danger 
              icon={<DeleteOutlined />}
            />
          </Tooltip>
        </Popconfirm>
      ]}
    >
      <Descriptions title={rule.rule_name || rule.name} size="small" column={1}>
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>规则管理</Title>
        <Space>
          <Button 
            type="default" 
            icon={<ReloadOutlined />}
            onClick={handleReloadRules}
            loading={loading}
          >
            重新加载
          </Button>
        </Space>
      </div>
      
      <Alert
        message="规则配置说明"
        description={
          <div>
            <p>1. 补全规则：自动填充缺失的字段值</p>
            <p>2. 校验规则：验证字段是否符合业务要求</p>
            <p>3. 优先级数值越大，执行顺序越靠前</p>
            <p>4. 支持CEL表达式语法和自定义函数</p>
            <p>5. <strong>新功能：</strong>极简数据库查询语法 <code>db.table.field[条件]</code></p>
            <p style={{ marginLeft: 20, fontSize: '12px' }}>
              例如：<code>db.companies.tax_number[name=invoice.supplier.name]</code>
            </p>
          </div>
        }
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Spin spinning={loading}>
        <Tabs 
          defaultActiveKey="1"
          tabBarExtraContent={
            <Space>
              <Button 
                type="primary" 
                icon={<PlusOutlined />}
                onClick={() => handleCreateRule('completion')}
              >
                新建补全规则
              </Button>
              <Button 
                type="primary" 
                icon={<PlusOutlined />}
                onClick={() => handleCreateRule('validation')}
              >
                新建校验规则
              </Button>
            </Space>
          }
        >
          <Tabs.TabPane 
            tab={
              <span>
                补全规则 
                <Badge 
                  count={completionRules.length} 
                  style={{ backgroundColor: '#52c41a', marginLeft: 8 }} 
                />
              </span>
            } 
            key="1"
          >
            {completionRules.length > 0 ? (
              completionRules.map(renderCompletionRule)
            ) : (
              <Card style={{ textAlign: 'center', padding: '40px 0' }}>
                <Text type="secondary">暂无补全规则</Text>
                <br />
                <Button 
                  type="link" 
                  icon={<PlusOutlined />}
                  onClick={() => handleCreateRule('completion')}
                >
                  创建第一个补全规则
                </Button>
              </Card>
            )}
          </Tabs.TabPane>
          
          <Tabs.TabPane 
            tab={
              <span>
                校验规则 
                <Badge 
                  count={validationRules.length} 
                  style={{ backgroundColor: '#1890ff', marginLeft: 8 }} 
                />
              </span>
            } 
            key="2"
          >
            {validationRules.length > 0 ? (
              validationRules.map(renderValidationRule)
            ) : (
              <Card style={{ textAlign: 'center', padding: '40px 0' }}>
                <Text type="secondary">暂无校验规则</Text>
                <br />
                <Button 
                  type="link" 
                  icon={<PlusOutlined />}
                  onClick={() => handleCreateRule('validation')}
                >
                  创建第一个校验规则
                </Button>
              </Card>
            )}
          </Tabs.TabPane>
        </Tabs>
      </Spin>

      <RuleEditor
        visible={editorVisible}
        onCancel={() => setEditorVisible(false)}
        onSave={handleSaveRule}
        rule={editingRule}
        ruleType={currentRuleType}
        mode={editorMode}
      />
    </div>
  );
};

export default RulesManager;