import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Select,
  Input,
  InputNumber,
  Button,
  Space,
  Row,
  Col,
  Typography,
  Tooltip,
  Switch,
  Divider,
  message
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  QuestionCircleOutlined
} from '@ant-design/icons';

const { Option } = Select;
const { Text } = Typography;

interface Condition {
  id: string;
  field: string;
  operator: string;
  value: string | number | boolean;
  logicalOperator?: 'AND' | 'OR';
}

interface ConditionBuilderProps {
  value?: string;
  onChange?: (expression: string) => void;
  domainFields?: any;
  placeholder?: string;
}

const OPERATORS = [
  { value: '==', label: '等于 (==)' },
  { value: '!=', label: '不等于 (!=)' },
  { value: '>', label: '大于 (>)' },
  { value: '>=', label: '大于等于 (>=)' },
  { value: '<', label: '小于 (<)' },
  { value: '<=', label: '小于等于 (<=)' },
  { value: 'contains', label: '包含' },
  { value: 'matches', label: '正则匹配' },
  { value: 'has', label: '字段存在' }
];

const LOGICAL_OPERATORS = [
  { value: 'AND', label: '并且 (&&)' },
  { value: 'OR', label: '或者 (||)' }
];

const ConditionBuilder: React.FC<ConditionBuilderProps> = ({
  value,
  onChange,
  domainFields,
  placeholder
}) => {
  const [conditions, setConditions] = useState<Condition[]>([]);
  const [visualMode, setVisualMode] = useState(false);
  const prevValueRef = useRef<string>();
  const isInitializedRef = useRef(false);
  const textValueRef = useRef<string>('');

  // 初始化效果：只在组件挂载时运行
  useEffect(() => {
    if (!isInitializedRef.current) {
      if (value && value.trim()) {
        textValueRef.current = value;
        // 简单情况下才启用可视化模式
        if (isSimpleExpression(value)) {
          parseExpression(value);
          setVisualMode(true);
        } else {
          setVisualMode(false);
        }
      } else {
        textValueRef.current = '';
        setConditions([createEmptyCondition()]);
        setVisualMode(true);
      }
      prevValueRef.current = value;
      isInitializedRef.current = true;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 监听外部值变化
  useEffect(() => {
    if (isInitializedRef.current && value !== prevValueRef.current) {
      if (value && value.trim()) {
        textValueRef.current = value;
        if (isSimpleExpression(value)) {
          parseExpression(value);
          setVisualMode(true);
        } else {
          setVisualMode(false);
        }
      } else {
        textValueRef.current = '';
        setConditions([createEmptyCondition()]);
        setVisualMode(true);
      }
      prevValueRef.current = value;
    }
  }, [value]);

  const isSimpleExpression = (expr: string): boolean => {
    // 检查是否是简单的表达式（不包含复杂的函数调用等）
    const complexPatterns = [
      /\w+\([^)]*\)/, // 函数调用
      /\[.*\]/, // 数组访问
      /map\(/, // 复杂的数组操作
      /filter\(/, 
      /reduce\(/
    ];
    
    return !complexPatterns.some(pattern => pattern.test(expr));
  };

  const parseExpression = (expr: string) => {
    // 简单的表达式解析（仅支持基本的条件）
    try {
      const parts = expr.split(/\s*(&&|\|\|)\s*/);
      const newConditions: Condition[] = [];
      
      parts.forEach((part, index) => {
        const trimmedPart = part.trim();
        if (trimmedPart === '&&' || trimmedPart === '||') return;
        
        // 解析单个条件
        const condition = parseSingleCondition(trimmedPart);
        if (condition) {
          if (index > 0) {
            condition.logicalOperator = parts[index - 1]?.trim() === '||' ? 'OR' : 'AND';
          }
          newConditions.push(condition);
        }
      });
      
      if (newConditions.length > 0) {
        setConditions(newConditions);
      }
    } catch (error) {
      console.warn('表达式解析失败，使用文本模式');
    }
  };

  const parseSingleCondition = (conditionStr: string): Condition | null => {
    // 匹配各种操作符
    const operators = ['>=', '<=', '==', '!=', '>', '<'];
    
    for (const op of operators) {
      const index = conditionStr.indexOf(op);
      if (index > 0) {
        const field = conditionStr.substring(0, index).trim();
        const value = conditionStr.substring(index + op.length).trim();
        
        return {
          id: generateId(),
          field: field.replace(/^invoice\./, ''), // 移除 invoice. 前缀
          operator: op,
          value: parseValue(value)
        };
      }
    }
    
    // 检查 has() 函数
    const hasMatch = conditionStr.match(/has\(([^)]+)\)/);
    if (hasMatch) {
      return {
        id: generateId(),
        field: hasMatch[1].replace(/^invoice\./, ''),
        operator: 'has',
        value: true
      };
    }
    
    return null;
  };

  const parseValue = (valueStr: string): string | number | boolean => {
    // 移除引号
    const cleaned = valueStr.replace(/^['"]|['"]$/g, '');
    
    // 特殊值处理
    if (cleaned === 'null') return 'null';
    if (cleaned === 'undefined') return 'undefined';
    
    // 尝试解析为数字
    if (!isNaN(Number(cleaned))) {
      return Number(cleaned);
    }
    
    // 布尔值
    if (cleaned === 'true') return true;
    if (cleaned === 'false') return false;
    
    return cleaned;
  };

  const generateId = () => Math.random().toString(36).substr(2, 9);

  const createEmptyCondition = (): Condition => ({
    id: generateId(),
    field: '',
    operator: '==',
    value: ''
  });

  const handleModeChange = (newVisualMode: boolean) => {
    if (newVisualMode) {
      // 切换到可视化模式
      const currentText = textValueRef.current || value || '';
      if (currentText && currentText.trim()) {
        if (isSimpleExpression(currentText)) {
          parseExpression(currentText);
        } else {
          // 复杂表达式无法解析，保持文本模式，不切换
          message.warning('当前表达式过于复杂，无法在可视化模式下编辑，请使用文本模式');
          return; // 不切换模式
        }
      } else {
        setConditions([createEmptyCondition()]);
      }
    } else {
      // 切换到文本模式
      const currentExpression = generateExpression();
      textValueRef.current = currentExpression;
      // 触发外部onChange以保持同步
      if (currentExpression !== value) {
        onChange?.(currentExpression);
      }
    }
    setVisualMode(newVisualMode);
  };

  const addCondition = () => {
    setConditions([...conditions, createEmptyCondition()]);
  };

  const removeCondition = (id: string) => {
    if (conditions.length > 1) {
      setConditions(conditions.filter(c => c.id !== id));
    }
  };

  const updateCondition = (id: string, updates: Partial<Condition>) => {
    setConditions(conditions.map(c => 
      c.id === id ? { ...c, ...updates } : c
    ));
  };

  const generateExpression = (): string => {
    if (conditions.length === 0) return '';
    
    return conditions.map((condition, index) => {
      let expr = '';
      
      // 添加逻辑操作符（除了第一个条件）
      if (index > 0) {
        expr += condition.logicalOperator === 'OR' ? ' || ' : ' && ';
      }
      
      // 生成条件表达式
      const fieldPath = condition.field.startsWith('invoice.') 
        ? condition.field 
        : `invoice.${condition.field}`;
      
      switch (condition.operator) {
        case 'has':
          expr += `has(${fieldPath})`;
          break;
        case 'contains':
          expr += `${fieldPath}.contains('${condition.value}')`;
          break;
        case 'matches':
          expr += `${fieldPath}.matches('${condition.value}')`;
          break;
        default:
          let valueStr;
          if (typeof condition.value === 'string') {
            // 特殊值不加引号
            if (condition.value === 'null' || condition.value === 'undefined') {
              valueStr = condition.value;
            } else {
              valueStr = `'${condition.value}'`;
            }
          } else {
            valueStr = condition.value;
          }
          expr += `${fieldPath} ${condition.operator} ${valueStr}`;
      }
      
      return expr;
    }).join('');
  };

  useEffect(() => {
    if (visualMode && conditions.length > 0 && isInitializedRef.current) {
      const expression = generateExpression();
      // 只有当表达式与当前值不同时才触发onChange
      if (expression !== value && expression !== prevValueRef.current) {
        onChange?.(expression);
        prevValueRef.current = expression;
      }
    }
  }, [conditions, visualMode]);

  const renderFieldOptions = (fields: any, prefix = ''): any[] => {
    if (!fields) return [];
    
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

  const renderCondition = (condition: Condition, index: number) => (
    <Card key={condition.id} size="small" style={{ marginBottom: 8 }}>
      <Row gutter={8} align="middle">
        {index > 0 && (
          <Col span={3}>
            <Select
              value={condition.logicalOperator || 'AND'}
              onChange={(value) => updateCondition(condition.id, { logicalOperator: value })}
              size="small"
            >
              {LOGICAL_OPERATORS.map(op => (
                <Option key={op.value} value={op.value}>{op.label}</Option>
              ))}
            </Select>
          </Col>
        )}
        
        <Col span={index > 0 ? 6 : 7}>
          <Select
            value={condition.field}
            onChange={(value) => updateCondition(condition.id, { field: value })}
            placeholder="选择字段"
            showSearch
            size="small"
            style={{ width: '100%' }}
          >
            {domainFields && renderFieldOptions(domainFields.invoice?.fields)}
          </Select>
        </Col>
        
        <Col span={4}>
          <Select
            value={condition.operator}
            onChange={(value) => updateCondition(condition.id, { operator: value })}
            size="small"
          >
            {OPERATORS.map(op => (
              <Option key={op.value} value={op.value}>{op.label}</Option>
            ))}
          </Select>
        </Col>
        
        {condition.operator !== 'has' && (
          <Col span={6}>
            {typeof condition.value === 'number' ? (
              <InputNumber
                value={condition.value}
                onChange={(value) => updateCondition(condition.id, { value: value || 0 })}
                size="small"
                style={{ width: '100%' }}
              />
            ) : (
              <Input
                value={condition.value as string}
                onChange={(e) => updateCondition(condition.id, { value: e.target.value })}
                placeholder="输入值"
                size="small"
              />
            )}
          </Col>
        )}
        
        <Col span={2}>
          <Space>
            {conditions.length > 1 && (
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                onClick={() => removeCondition(condition.id)}
              />
            )}
          </Space>
        </Col>
      </Row>
    </Card>
  );

  return (
    <div>
      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Text>编辑模式:</Text>
          <Switch
            checked={visualMode}
            onChange={handleModeChange}
            checkedChildren="可视化"
            unCheckedChildren="文本"
          />
          <Tooltip title="简单条件支持可视化编辑，复杂表达式请使用文本模式">
            <QuestionCircleOutlined />
          </Tooltip>
        </Space>
        
        {visualMode && (
          <Button
            type="dashed"
            size="small"
            icon={<PlusOutlined />}
            onClick={addCondition}
          >
            添加条件
          </Button>
        )}
      </div>
      
      {visualMode ? (
        <div>
          {conditions.map((condition, index) => renderCondition(condition, index))}
          
          <Divider style={{ margin: '8px 0' }} />
          <Text type="secondary" style={{ fontSize: '12px' }}>
            生成的表达式: {generateExpression() || '(空)'}
          </Text>
        </div>
      ) : (
        <Input.TextArea
          value={textValueRef.current || value || ''}
          onChange={(e) => {
            textValueRef.current = e.target.value;
            onChange?.(e.target.value);
          }}
          placeholder={placeholder}
          rows={3}
        />
      )}
    </div>
  );
};

export default ConditionBuilder;