import React, { useState, useEffect } from 'react';
import { Card, List, Typography, Tag, Spin } from 'antd';
import { ApiOutlined } from '@ant-design/icons';
import { invoiceService } from '../services/api';

const { Title, Text } = Typography;

interface Connector {
  name: string;
  description: string;
}

const ConnectorList: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [connectors, setConnectors] = useState<Connector[]>([]);

  useEffect(() => {
    loadConnectors();
  }, []);

  const loadConnectors = async () => {
    setLoading(true);
    try {
      const response = await invoiceService.getConnectors();
      if (response.data.success) {
        setConnectors(response.data.data);
      }
    } catch (error) {
      console.error('Failed to load connectors:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Title level={3}>业务连接器</Title>
      
      <Card>
        <Text type="secondary">
          业务连接器负责将外部系统的数据转换为标准KDUBL格式。在本Demo中，假设UBL就是KDUBL，所以连接器只是预留的框架。
        </Text>
      </Card>

      <Spin spinning={loading}>
        <List
          style={{ marginTop: 16 }}
          grid={{ gutter: 16, xs: 1, sm: 1, md: 2, lg: 2, xl: 2 }}
          dataSource={connectors}
          renderItem={(connector) => (
            <List.Item>
              <Card
                title={
                  <span>
                    <ApiOutlined /> {connector.name}
                  </span>
                }
              >
                <p>{connector.description}</p>
                <Tag color="blue">Mock实现</Tag>
              </Card>
            </List.Item>
          )}
        />
      </Spin>
    </div>
  );
};

export default ConnectorList;