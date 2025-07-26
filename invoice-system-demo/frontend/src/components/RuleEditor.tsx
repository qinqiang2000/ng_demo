import React, { useState, useEffect } from 'react';
import {
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  Button,
  Select,
  Tabs,
  Card,
  Alert,
  Tooltip,
  Space,
  message,
  Collapse,
  Typography
} from 'antd';
import {
  QuestionCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  BulbOutlined,
  ApiOutlined
} from '@ant-design/icons';
import { invoiceService } from '../services/api';
import ConditionBuilder from './ConditionBuilder';
import ExpressionTemplates from './ExpressionTemplates';
import { RuleFormData, DomainField, FunctionInfo } from '../types/rules';

const { TextArea } = Input;
const { Option } = Select;
const { TabPane } = Tabs;
const { Panel } = Collapse;
const { Text, Link } = Typography;

interface RuleEditorProps {
  visible: boolean;
  onCancel: () => void;
  onSave: (rule: RuleFormData) => void;
  rule?: RuleFormData;
  ruleType: 'completion' | 'validation';
  mode: 'create' | 'edit';
}

const RuleEditor: React.FC<RuleEditorProps> = ({
  visible,
  onCancel,
  onSave,
  rule,
  ruleType,
  mode
}) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [validating, setValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<any>(null);
  const [domainFields, setDomainFields] = useState<any>(null);
  const [functions, setFunctions] = useState<any>(null);
  const [llmStatus, setLlmStatus] = useState<any>(null);
  const [llmLoading, setLlmLoading] = useState(false);

  useEffect(() => {
    if (visible) {
      loadHelperData();
      if (rule) {
        form.setFieldsValue(rule);
      } else {
        form.resetFields();
        // 设置默认值
        form.setFieldsValue({
          priority: ruleType === 'completion' ? 50 : 100,
          active: true,
          apply_to: ''
        });
      }
    }
  }, [visible, rule, form, ruleType]);

  const loadHelperData = async () => {
    try {
      const [fieldsRes, functionsRes, llmRes] = await Promise.all([
        invoiceService.getDomainFields(),
        invoiceService.getAvailableFunctions(),
        invoiceService.getLLMStatus()
      ]);
      
      setDomainFields(fieldsRes.data.data);
      setFunctions(functionsRes.data.data);
      setLlmStatus(llmRes.data.data);
    } catch (error) {
      console.error('加载帮助数据失败:', error);
    }
  };

  const handleSave = async () => {
    try {
      setLoading(true);
      const values = await form.validateFields();
      
      // 验证表达式
      const validationRes = await invoiceService.validateExpression({
        expression: values.rule_expression,
        rule_type: ruleType
      });

      if (!validationRes.data.data.valid) {
        message.error(`表达式语法错误: ${validationRes.data.data.error}`);
        setValidationResult(validationRes.data.data);
        return;
      }

      onSave(values);
      form.resetFields();
      setValidationResult(null);
    } catch (error) {
      console.error('保存规则失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleValidateExpression = async () => {
    const expression = form.getFieldValue('rule_expression');
    if (!expression) {
      message.warning('请先输入表达式');
      return;
    }

    try {
      setValidating(true);
      const res = await invoiceService.validateExpression({
        expression,
        rule_type: ruleType
      });
      
      setValidationResult(res.data.data);
      if (res.data.data.valid) {
        message.success('表达式语法验证通过');
      } else {
        message.error('表达式语法验证失败');
      }
    } catch (error) {
      console.error('验证表达式失败:', error);
      message.error('验证表达式失败');
    } finally {
      setValidating(false);
    }
  };

  const handleLLMGenerate = async () => {
    const description = form.getFieldValue('llm_description');
    if (!description) {
      message.warning('请先输入需求描述');
      return;
    }

    try {
      setLlmLoading(true);
      const res = await invoiceService.generateRuleWithLLM({
        description,
        rule_type: ruleType
      });

      if (res.data.success) {
        const generatedRule = res.data.data;
        form.setFieldsValue({
          rule_name: generatedRule.rule_name,
          apply_to: generatedRule.apply_to || '',
          target_field: generatedRule.target_field,
          field_path: generatedRule.field_path,
          rule_expression: generatedRule.rule_expression,
          error_message: generatedRule.error_message,
          priority: generatedRule.priority,
          active: generatedRule.active
        });
        message.success('规则生成成功！请检查并调整生成的规则');
      } else {
        message.error(`规则生成失败: ${res.data.error}`);
      }
    } catch (error) {
      console.error('LLM生成规则失败:', error);
      message.error('LLM生成规则失败');
    } finally {
      setLlmLoading(false);
    }
  };

  const renderFieldOptions = (fields: { [key: string]: DomainField }, prefix = '') => {
    const options: any[] = [];
    
    Object.keys(fields).forEach(key => {
      const field = fields[key];
      const fullPath = prefix ? `${prefix}.${key}` : key;
      
      if (field.type === 'object' && field.fields) {
        options.push(
          <Option key={fullPath} value={fullPath}>
            {fullPath} ({field.description})
          </Option>
        );
        options.push(...renderFieldOptions(field.fields, fullPath));
      } else if (field.type === 'array' && field.item_fields) {
        options.push(
          <Option key={fullPath} value={fullPath}>
            {fullPath} ({field.description})
          </Option>
        );
        options.push(...renderFieldOptions(field.item_fields, `${fullPath}[]`));
      } else {
        options.push(
          <Option key={fullPath} value={fullPath}>
            {fullPath} ({field.description})
          </Option>
        );
      }
    });
    
    return options;
  };

  const renderFunctionHelp = () => {
    if (!functions) return null;

    return (
      <Collapse size="small" ghost>
        <Panel header="CEL内置函数" key="cel">
          <Space direction="vertical" style={{ width: '100%' }}>
            {Object.entries(functions.cel_builtin.functions).map(([name, info]: [string, any]) => (
              <Card key={name} size="small">
                <Text strong>{name}</Text>
                <br />
                <Text type="secondary">{info.description}</Text>
                <br />
                <Text code>{info.example}</Text>
              </Card>
            ))}
          </Space>
        </Panel>
        <Panel header="自定义函数" key="custom">
          <Space direction="vertical" style={{ width: '100%' }}>
            {Object.entries(functions.custom_functions.functions).map(([name, info]: [string, any]) => (
              <Card key={name} size="small">
                <Text strong>{name}</Text>
                <br />
                <Text type="secondary">{info.description}</Text>
                <br />
                <Text code>{info.example}</Text>
                {info.note && (
                  <>
                    <br />
                    <Text type="warning">注意: {info.note}</Text>
                  </>
                )}
              </Card>
            ))}
          </Space>
        </Panel>
        <Panel header="操作符" key="operators">
          <Space wrap>
            {Object.entries(functions.operators.list).map(([op, desc]: [string, any]) => (
              <Text key={op} code>{op} ({desc})</Text>
            ))}
          </Space>
        </Panel>
      </Collapse>
    );
  };

  return (
    <Modal
      title={`${mode === 'create' ? '新建' : '编辑'}${ruleType === 'completion' ? '补全' : '校验'}规则`}
      open={visible}
      onCancel={onCancel}
      width={800}
      footer={[
        <Button key="cancel" onClick={onCancel}>
          取消
        </Button>,
        <Button key="save" type="primary" loading={loading} onClick={handleSave}>
          保存
        </Button>
      ]}
    >
      <Tabs defaultActiveKey="basic">
        <TabPane tab="基本信息" key="basic">
          <Form form={form} layout="vertical">
            <Form.Item
              name="rule_name"
              label="规则名称"
              rules={[{ required: true, message: '请输入规则名称' }]}
            >
              <Input placeholder="请输入规则名称" />
            </Form.Item>

            <Form.Item
              name="apply_to"
              label={
                <span>
                  应用条件
                  <Tooltip title="CEL表达式，用于判断何时应用此规则。留空表示无条件应用">
                    <QuestionCircleOutlined style={{ marginLeft: 4 }} />
                  </Tooltip>
                </span>
              }
            >
              <ConditionBuilder
                value={form.getFieldValue('apply_to')}
                onChange={(value) => form.setFieldsValue({ apply_to: value })}
                domainFields={domainFields}
                placeholder="如: invoice.total_amount > 1000"
              />
            </Form.Item>

            {ruleType === 'completion' ? (
              <Form.Item
                name="target_field"
                label="目标字段"
                rules={[{ required: true, message: '请选择目标字段' }]}
              >
                <Select
                  placeholder="请选择要填充的字段"
                  showSearch
                  optionFilterProp="children"
                >
                  {domainFields && renderFieldOptions(domainFields.invoice.fields, 'invoice')}
                </Select>
              </Form.Item>
            ) : (
              <Form.Item
                name="field_path"
                label="校验字段"
                rules={[{ required: true, message: '请选择校验字段' }]}
              >
                <Select
                  placeholder="请选择要校验的字段"
                  showSearch
                  optionFilterProp="children"
                >
                  {domainFields && renderFieldOptions(domainFields.invoice.fields, 'invoice')}
                </Select>
              </Form.Item>
            )}

            <Form.Item
              name="rule_expression"
              label={
                <span>
                  {ruleType === 'completion' ? '计算表达式' : '校验表达式'}
                  <Tooltip title={ruleType === 'completion' ? '用于计算字段值的CEL表达式' : '返回boolean的CEL表达式，true表示通过校验'}>
                    <QuestionCircleOutlined style={{ marginLeft: 4 }} />
                  </Tooltip>
                </span>
              }
              rules={[{ required: true, message: '请输入表达式' }]}
            >
              <TextArea
                rows={3}
                placeholder={
                  ruleType === 'completion'
                    ? "如: invoice.total_amount * 0.06 或 db.companies.tax_number[name=invoice.supplier.name]"
                    : "如: has(invoice.supplier.name) && invoice.supplier.name != ''"
                }
              />
            </Form.Item>

            {ruleType === 'validation' && (
              <Form.Item
                name="error_message"
                label="错误信息"
                rules={[{ required: true, message: '请输入错误信息' }]}
              >
                <Input placeholder="校验失败时显示的错误信息" />
              </Form.Item>
            )}

            <Form.Item
              name="priority"
              label={
                <span>
                  优先级
                  <Tooltip title="数值越大优先级越高，优先执行">
                    <QuestionCircleOutlined style={{ marginLeft: 4 }} />
                  </Tooltip>
                </span>
              }
              rules={[{ required: true, message: '请输入优先级' }]}
            >
              <InputNumber min={1} max={200} style={{ width: '100%' }} />
            </Form.Item>

            <Form.Item name="active" label="启用状态" valuePropName="checked">
              <Switch />
            </Form.Item>

            <Form.Item>
              <Space>
                <Button 
                  type="default" 
                  loading={validating} 
                  onClick={handleValidateExpression}
                  icon={<CheckCircleOutlined />}
                >
                  验证表达式
                </Button>
              </Space>
            </Form.Item>

            {validationResult && (
              <Alert
                type={validationResult.valid ? 'success' : 'error'}
                message={
                  validationResult.valid
                    ? `表达式验证通过 (结果: ${validationResult.result})`
                    : `表达式验证失败: ${validationResult.error}`
                }
                description={
                  !validationResult.valid && validationResult.suggestion
                    ? validationResult.suggestion
                    : null
                }
                style={{ marginTop: 8 }}
              />
            )}
          </Form>
        </TabPane>

        {llmStatus?.available && (
          <TabPane tab={<span><BulbOutlined />AI助手</span>} key="llm">
            <Alert
              message="AI规则生成"
              description="使用自然语言描述您的需求，AI将为您生成相应的规则配置"
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
            />
            
            <Form.Item label="需求描述">
              <TextArea
                rows={4}
                placeholder={
                  ruleType === 'completion'
                    ? "请描述需要自动填充的字段和计算逻辑，如：当发票总金额大于0且没有税额时，按6%税率计算税额"
                    : "请描述需要校验的业务规则，如：大额发票必须有客户税号，金额超过5000元的发票客户税号不能为空"
                }
                onChange={(e) => form.setFieldsValue({ llm_description: e.target.value })}
              />
            </Form.Item>

            <Button
              type="primary"
              loading={llmLoading}
              onClick={handleLLMGenerate}
              icon={<ApiOutlined />}
            >
              生成规则
            </Button>

            <Alert
              message="提示"
              description="AI生成的规则仅供参考，请仔细检查并根据实际需求调整"
              type="warning"
              showIcon
              style={{ marginTop: 16 }}
            />
          </TabPane>
        )}

        <TabPane tab="表达式模板" key="templates">
          <ExpressionTemplates
            ruleType={ruleType}
            onUseTemplate={(expression) => {
              form.setFieldsValue({ rule_expression: expression });
            }}
          />
        </TabPane>

        <TabPane tab="帮助文档" key="help">
          <Alert
            message="表达式语法帮助"
            description="以下是可用的字段、函数和操作符参考"
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
          
          {renderFunctionHelp()}
          
          {domainFields && (
            <Card title="可用字段" style={{ marginTop: 16 }}>
              <Collapse size="small" ghost>
                <Panel header="发票字段结构" key="fields">
                  <pre style={{ fontSize: '12px', background: '#f5f5f5', padding: '8px' }}>
                    {JSON.stringify(domainFields, null, 2)}
                  </pre>
                </Panel>
              </Collapse>
            </Card>
          )}
        </TabPane>
      </Tabs>
    </Modal>
  );
};

export default RuleEditor;