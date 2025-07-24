import React, { useState } from 'react';
import { Layout, Menu, theme } from 'antd';
import {
  FileTextOutlined,
  SettingOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  AppstoreOutlined,
} from '@ant-design/icons';
import InvoiceProcessor from './components/InvoiceProcessor';
import BatchInvoiceProcessor from './components/BatchInvoiceProcessor';
import RulesManager from './components/RulesManager';
import ConnectorList from './components/ConnectorList';
import DataManagement from './components/DataManagement';
import './App.css';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [selectedKey, setSelectedKey] = useState('1');
  const {
    token: { colorBgContainer },
  } = theme.useToken();

  const renderContent = () => {
    switch (selectedKey) {
      case '1':
        return <InvoiceProcessor />;
      case '2':
        return <BatchInvoiceProcessor />;
      case '3':
        return <RulesManager />;
      case '4':
        return <ConnectorList />;
      case '5':
        return <DataManagement />;
      default:
        return <InvoiceProcessor />;
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center' }}>
        <div style={{ color: 'white', fontSize: '20px', fontWeight: 'bold' }}>
          <FileTextOutlined /> 下一代开票系统 MVP Demo
        </div>
      </Header>
      <Layout>
        <Sider width={200} style={{ background: colorBgContainer }}>
          <Menu
            mode="inline"
            defaultSelectedKeys={['1']}
            selectedKeys={[selectedKey]}
            style={{ height: '100%', borderRight: 0 }}
            onSelect={({ key }) => setSelectedKey(key)}
            items={[
              {
                key: '1',
                icon: <CheckCircleOutlined />,
                label: '发票处理',
              },
              {
                key: '2',
                icon: <AppstoreOutlined />,
                label: '批量处理',
              },
              {
                key: '3',
                icon: <SettingOutlined />,
                label: '规则管理',
              },
              {
                key: '4',
                icon: <ApiOutlined />,
                label: '连接器',
              },
              {
                key: '5',
                icon: <DatabaseOutlined />,
                label: '数据管理',
              },
            ]}
          />
        </Sider>
        <Layout style={{ padding: '24px' }}>
          <Content
            style={{
              padding: 24,
              margin: 0,
              minHeight: 280,
              background: colorBgContainer,
            }}
          >
            {renderContent()}
          </Content>
        </Layout>
      </Layout>
    </Layout>
  );
};

export default App;