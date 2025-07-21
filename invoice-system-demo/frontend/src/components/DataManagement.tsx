import React, { useState, useEffect } from 'react';
import { 
  Card, 
  Table, 
  Button, 
  Modal, 
  Form, 
  Input, 
  Select, 
  InputNumber,
  Space,
  message,
  Popconfirm,
  Tabs,
  Switch,
  Row,
  Col,
  Statistic
} from 'antd';
import { 
  PlusOutlined, 
  EditOutlined, 
  DeleteOutlined, 
  ReloadOutlined,
  SearchOutlined
} from '@ant-design/icons';
import { dataApi } from '../services/api';

const { Option } = Select;
const { TabPane } = Tabs;

interface Company {
  id: number;
  name: string;
  tax_number?: string;
  address?: string;
  phone?: string;
  email?: string;
  category: string;
  is_active: boolean;
}

interface TaxRate {
  id: number;
  name: string;
  rate: number;
  category?: string;
  min_amount: number;
  max_amount?: number;
  description?: string;
  is_active: boolean;
}

interface Stats {
  companies: {
    total: number;
    active: number;
    categories: Record<string, number>;
  };
  tax_rates: {
    total: number;
    active: number;
    categories: Record<string, number>;
  };
}

const DataManagement: React.FC = () => {
  // 状态管理
  const [companies, setCompanies] = useState<Company[]>([]);
  const [taxRates, setTaxRates] = useState<TaxRate[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(false);

  // 模态框状态
  const [companyModalVisible, setCompanyModalVisible] = useState(false);
  const [taxRateModalVisible, setTaxRateModalVisible] = useState(false);
  const [editingCompany, setEditingCompany] = useState<Company | null>(null);
  const [editingTaxRate, setEditingTaxRate] = useState<TaxRate | null>(null);

  // 搜索状态
  const [companySearchTerm, setCompanySearchTerm] = useState('');
  const [taxRateCategory, setTaxRateCategory] = useState<string>('');

  // 表单
  const [companyForm] = Form.useForm();
  const [taxRateForm] = Form.useForm();

  // 加载数据
  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      await Promise.all([
        loadCompanies(),
        loadTaxRates(),
        loadStats()
      ]);
    } catch (error) {
      console.error('加载数据失败:', error);
      message.error('加载数据失败');
    } finally {
      setLoading(false);
    }
  };

  const loadCompanies = async () => {
    const params: any = {};
    if (companySearchTerm) {
      params.name_pattern = companySearchTerm;
    }
    const response = await dataApi.getCompanies(params);
    setCompanies(response);
  };

  const loadTaxRates = async () => {
    const params: any = {};
    if (taxRateCategory) {
      params.category = taxRateCategory;
    }
    const response = await dataApi.getTaxRates(params);
    setTaxRates(response);
  };

  const loadStats = async () => {
    const response = await dataApi.getStats();
    setStats(response);
  };

  // 企业管理
  const handleCreateCompany = async (values: any) => {
    try {
      await dataApi.createCompany(values);
      message.success('企业创建成功');
      setCompanyModalVisible(false);
      companyForm.resetFields();
      loadCompanies();
      loadStats();
    } catch (error) {
      console.error('创建企业失败:', error);
      message.error('创建企业失败');
    }
  };

  const handleUpdateCompany = async (values: any) => {
    if (!editingCompany) return;
    try {
      await dataApi.updateCompany(editingCompany.id, values);
      message.success('企业更新成功');
      setCompanyModalVisible(false);
      setEditingCompany(null);
      companyForm.resetFields();
      loadCompanies();
      loadStats();
    } catch (error) {
      console.error('更新企业失败:', error);
      message.error('更新企业失败');
    }
  };

  const handleDeleteCompany = async (id: number) => {
    try {
      await dataApi.deleteCompany(id);
      message.success('企业删除成功');
      loadCompanies();
      loadStats();
    } catch (error) {
      console.error('删除企业失败:', error);
      message.error('删除企业失败');
    }
  };

  // 税率管理
  const handleCreateTaxRate = async (values: any) => {
    try {
      await dataApi.createTaxRate(values);
      message.success('税率配置创建成功');
      setTaxRateModalVisible(false);
      taxRateForm.resetFields();
      loadTaxRates();
      loadStats();
    } catch (error) {
      console.error('创建税率配置失败:', error);
      message.error('创建税率配置失败');
    }
  };

  const handleUpdateTaxRate = async (values: any) => {
    if (!editingTaxRate) return;
    try {
      await dataApi.updateTaxRate(editingTaxRate.id, values);
      message.success('税率配置更新成功');
      setTaxRateModalVisible(false);
      setEditingTaxRate(null);
      taxRateForm.resetFields();
      loadTaxRates();
      loadStats();
    } catch (error) {
      console.error('更新税率配置失败:', error);
      message.error('更新税率配置失败');
    }
  };

  const handleDeleteTaxRate = async (id: number) => {
    try {
      await dataApi.deleteTaxRate(id);
      message.success('税率配置删除成功');
      loadTaxRates();
      loadStats();
    } catch (error) {
      console.error('删除税率配置失败:', error);
      message.error('删除税率配置失败');
    }
  };

  // 企业表格列
  const companyColumns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
    },
    {
      title: '企业名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '税号',
      dataIndex: 'tax_number',
      key: 'tax_number',
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      render: (category: string) => {
        const categoryMap: Record<string, string> = {
          'GENERAL': '一般企业',
          'TRAVEL_SERVICE': '旅游服务',
          'TECH': '科技企业',
          'TRADING': '贸易公司'
        };
        return categoryMap[category] || category;
      }
    },
    {
      title: '状态',
      dataIndex: 'is_active',
      key: 'is_active',
      render: (active: boolean) => (
        <Switch checked={active} disabled size="small" />
      )
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: any, record: Company) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingCompany(record);
              companyForm.setFieldsValue(record);
              setCompanyModalVisible(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除这个企业吗？"
            onConfirm={() => handleDeleteCompany(record.id)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // 税率表格列
  const taxRateColumns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
    },
    {
      title: '税率名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '税率',
      dataIndex: 'rate',
      key: 'rate',
      render: (rate: number) => `${(rate * 100).toFixed(2)}%`
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
    },
    {
      title: '最小金额',
      dataIndex: 'min_amount',
      key: 'min_amount',
      render: (amount: number) => `¥${amount.toLocaleString()}`
    },
    {
      title: '最大金额',
      dataIndex: 'max_amount',
      key: 'max_amount',
      render: (amount?: number) => amount ? `¥${amount.toLocaleString()}` : '无限制'
    },
    {
      title: '状态',
      dataIndex: 'is_active',
      key: 'is_active',
      render: (active: boolean) => (
        <Switch checked={active} disabled size="small" />
      )
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: any, record: TaxRate) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingTaxRate(record);
              taxRateForm.setFieldsValue(record);
              setTaxRateModalVisible(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除这个税率配置吗？"
            onConfirm={() => handleDeleteTaxRate(record.id)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Card title="数据管理" style={{ marginBottom: '16px' }}>
        {stats && (
          <Row gutter={16} style={{ marginBottom: '24px' }}>
            <Col span={6}>
              <Statistic title="企业总数" value={stats.companies.total} />
            </Col>
            <Col span={6}>
              <Statistic title="活跃企业" value={stats.companies.active} />
            </Col>
            <Col span={6}>
              <Statistic title="税率配置" value={stats.tax_rates.total} />
            </Col>
            <Col span={6}>
              <Statistic title="活跃税率" value={stats.tax_rates.active} />
            </Col>
          </Row>
        )}

        <Button 
          type="primary" 
          icon={<ReloadOutlined />} 
          onClick={loadData}
          loading={loading}
        >
          刷新数据
        </Button>
      </Card>

      <Tabs defaultActiveKey="companies">
        <TabPane tab="企业管理" key="companies">
          <Card>
            <div style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between' }}>
              <Space>
                <Input
                  placeholder="搜索企业名称"
                  prefix={<SearchOutlined />}
                  value={companySearchTerm}
                  onChange={(e) => setCompanySearchTerm(e.target.value)}
                  onPressEnter={loadCompanies}
                  style={{ width: 200 }}
                />
                <Button onClick={loadCompanies}>搜索</Button>
              </Space>
              <Button 
                type="primary" 
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditingCompany(null);
                  companyForm.resetFields();
                  setCompanyModalVisible(true);
                }}
              >
                新增企业
              </Button>
            </div>
            <Table 
              columns={companyColumns}
              dataSource={companies}
              rowKey="id"
              loading={loading}
              pagination={{ pageSize: 10 }}
            />
          </Card>
        </TabPane>

        <TabPane tab="税率管理" key="taxRates">
          <Card>
            <div style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between' }}>
              <Space>
                <Select
                  placeholder="筛选分类"
                  value={taxRateCategory}
                  onChange={setTaxRateCategory}
                  style={{ width: 200 }}
                  allowClear
                >
                  <Option value="GENERAL">一般税率</Option>
                  <Option value="TRAVEL_SERVICE">旅游服务</Option>
                  <Option value="TECH">科技企业</Option>
                </Select>
                <Button onClick={loadTaxRates}>筛选</Button>
              </Space>
              <Button 
                type="primary" 
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditingTaxRate(null);
                  taxRateForm.resetFields();
                  setTaxRateModalVisible(true);
                }}
              >
                新增税率配置
              </Button>
            </div>
            <Table 
              columns={taxRateColumns}
              dataSource={taxRates}
              rowKey="id"
              loading={loading}
              pagination={{ pageSize: 10 }}
            />
          </Card>
        </TabPane>
      </Tabs>

      {/* 企业编辑模态框 */}
      <Modal
        title={editingCompany ? '编辑企业' : '新增企业'}
        visible={companyModalVisible}
        onCancel={() => {
          setCompanyModalVisible(false);
          setEditingCompany(null);
          companyForm.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form
          form={companyForm}
          layout="vertical"
          onFinish={editingCompany ? handleUpdateCompany : handleCreateCompany}
        >
          <Form.Item
            label="企业名称"
            name="name"
            rules={[{ required: true, message: '请输入企业名称' }]}
          >
            <Input placeholder="请输入企业名称" />
          </Form.Item>
          <Form.Item
            label="税号"
            name="tax_number"
          >
            <Input placeholder="请输入税号" />
          </Form.Item>
          <Form.Item
            label="地址"
            name="address"
          >
            <Input.TextArea placeholder="请输入企业地址" rows={3} />
          </Form.Item>
          <Form.Item
            label="联系电话"
            name="phone"
          >
            <Input placeholder="请输入联系电话" />
          </Form.Item>
          <Form.Item
            label="邮箱"
            name="email"
          >
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item
            label="企业分类"
            name="category"
            initialValue="GENERAL"
          >
            <Select>
              <Option value="GENERAL">一般企业</Option>
              <Option value="TRAVEL_SERVICE">旅游服务</Option>
              <Option value="TECH">科技企业</Option>
              <Option value="TRADING">贸易公司</Option>
            </Select>
          </Form.Item>
          <Form.Item
            label="状态"
            name="is_active"
            valuePropName="checked"
            initialValue={true}
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {editingCompany ? '更新' : '创建'}
              </Button>
              <Button onClick={() => {
                setCompanyModalVisible(false);
                setEditingCompany(null);
                companyForm.resetFields();
              }}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 税率编辑模态框 */}
      <Modal
        title={editingTaxRate ? '编辑税率配置' : '新增税率配置'}
        visible={taxRateModalVisible}
        onCancel={() => {
          setTaxRateModalVisible(false);
          setEditingTaxRate(null);
          taxRateForm.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form
          form={taxRateForm}
          layout="vertical"
          onFinish={editingTaxRate ? handleUpdateTaxRate : handleCreateTaxRate}
        >
          <Form.Item
            label="税率名称"
            name="name"
            rules={[{ required: true, message: '请输入税率名称' }]}
          >
            <Input placeholder="请输入税率名称" />
          </Form.Item>
          <Form.Item
            label="税率"
            name="rate"
            rules={[{ required: true, message: '请输入税率' }]}
          >
            <InputNumber
              min={0}
              max={1}
              step={0.01}
              precision={4}
              placeholder="请输入税率（如0.06表示6%）"
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            label="分类"
            name="category"
          >
            <Select placeholder="请选择分类">
              <Option value="GENERAL">一般税率</Option>
              <Option value="TRAVEL_SERVICE">旅游服务</Option>
              <Option value="TECH">科技企业</Option>
            </Select>
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="最小适用金额"
                name="min_amount"
                initialValue={0}
              >
                <InputNumber
                  min={0}
                  precision={2}
                  placeholder="最小金额"
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="最大适用金额"
                name="max_amount"
              >
                <InputNumber
                  min={0}
                  precision={2}
                  placeholder="最大金额（可选）"
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            label="说明"
            name="description"
          >
            <Input.TextArea placeholder="税率说明" rows={3} />
          </Form.Item>
          <Form.Item
            label="状态"
            name="is_active"
            valuePropName="checked"
            initialValue={true}
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {editingTaxRate ? '更新' : '创建'}
              </Button>
              <Button onClick={() => {
                setTaxRateModalVisible(false);
                setEditingTaxRate(null);
                taxRateForm.resetFields();
              }}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DataManagement;