import React, { useState, useEffect } from 'react';
import {
  Card,
  Button,
  Upload,
  message,
  Spin,
  Alert,
  Typography,
  Space,
  Progress,
  Select,
  Divider,
  Table,
  Tag,
  Collapse,
  Row,
  Col,
  Statistic,
  Steps,
  Modal,
  List
} from 'antd';
import {
  UploadOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EyeOutlined,
  DownloadOutlined,
  MergeOutlined,
  ClockCircleOutlined
} from '@ant-design/icons';
import type { UploadProps, UploadFile } from 'antd';
import { invoiceService } from '../services/api';

const { Title, Text } = Typography;
const { Option } = Select;
const { Panel } = Collapse;
const { Step } = Steps;

interface BatchProcessResult {
  batch_id: string;
  overall_success: boolean;
  total_files: number;
  completion_stage: {
    successful: number;
    failed: number;
    results: any[];
  };
  merge_stage: {
    input_invoices: number;
    output_invoices: number;
    merged_invoices: any[];
  };
  validation_stage: {
    passed: number;
    failed: number;
    results: any[];
  };
  processing_time: number;
}

const BatchInvoiceProcessor: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [processResult, setProcessResult] = useState<BatchProcessResult | null>(null);
  const [processingProgress, setProcessingProgress] = useState(0);
  const [currentStage, setCurrentStage] = useState<string>('');
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedInvoiceDetail, setSelectedInvoiceDetail] = useState<any>(null);

  const uploadProps: UploadProps = {
    multiple: true,
    accept: '.xml',
    beforeUpload: (file) => {
      if (fileList.length >= 50) {
        message.error('最多只能上传50个文件');
        return false;
      }
      return false; // 阻止自动上传
    },
    onChange: (info) => {
      setFileList(info.fileList.slice(-50)); // 限制最多50个文件
    },
    onRemove: (file) => {
      setFileList(fileList.filter(item => item.uid !== file.uid));
    },
  };

  const handleBatchProcess = async () => {
    if (fileList.length === 0) {
      message.warning('请先上传XML文件');
      return;
    }

    setLoading(true);
    setProcessingProgress(0);
    setCurrentStage('准备处理...');
    
    try {
      const formData = new FormData();
      fileList.forEach((file) => {
        if (file.originFileObj) {
          formData.append('files', file.originFileObj);
        }
      });
      formData.append('source_system', 'ERP');
      formData.append('merge_strategy', 'none');
      
      // 模拟处理进度
      const progressInterval = setInterval(() => {
        setProcessingProgress(prev => {
          if (prev >= 90) {
            clearInterval(progressInterval);
            return prev;
          }
          return prev + 10;
        });
      }, 500);

      setCurrentStage('批量补全中...');
      
      const response = await invoiceService.processBatchInvoices(formData);
      
      clearInterval(progressInterval);
      setProcessingProgress(100);
      setCurrentStage('处理完成');
      
      setProcessResult(response.data);
      
      if (response.data.overall_success) {
        message.success(`批量处理完成！成功处理 ${response.data.total_files} 个文件`);
      } else {
        message.warning('批量处理完成，但部分文件处理失败');
      }
    } catch (error: any) {
      message.error('批量处理失败: ' + (error.response?.data?.detail || error.message));
    } finally {
      setLoading(false);
    }
  };

  const showInvoiceDetail = (invoice: any) => {
    setSelectedInvoiceDetail(invoice);
    setDetailModalVisible(true);
  };

  const renderFileList = () => {
    const columns = [
      {
        title: '文件名',
        dataIndex: 'name',
        key: 'name',
        render: (text: string) => (
          <span>
            <FileTextOutlined style={{ marginRight: 8 }} />
            {text}
          </span>
        ),
      },
      {
        title: '大小',
        dataIndex: 'size',
        key: 'size',
        render: (size: number) => `${(size / 1024).toFixed(1)} KB`,
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        render: () => <Tag color="blue">待处理</Tag>,
      },
      {
        title: '操作',
        key: 'action',
        render: (_: any, record: UploadFile) => (
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => setFileList(fileList.filter(item => item.uid !== record.uid))}
          >
            移除
          </Button>
        ),
      },
    ];

    return (
      <Table
        dataSource={fileList}
        columns={columns}
        pagination={false}
        size="small"
        rowKey="uid"
      />
    );
  };

  const renderProcessingSteps = () => {
    if (!processResult) return null;

    const steps: Array<{title: string; status: 'finish' | 'error' | 'wait' | 'process'; description: string}> = [
      {
        title: '文件上传',
        status: 'finish',
        description: `${processResult.total_files} 个文件`,
      },
      {
        title: '批量补全',
        status: processResult.completion_stage.successful > 0 ? 'finish' : 'error',
        description: `成功 ${processResult.completion_stage.successful}，失败 ${processResult.completion_stage.failed}`,
      },
      {
        title: '合并处理',
        status: 'finish',
        description: `${processResult.merge_stage.input_invoices} → ${processResult.merge_stage.output_invoices} 张发票`,
      },
      {
        title: '批量校验',
        status: processResult.validation_stage.passed > 0 ? 'finish' : 'error',
        description: `通过 ${processResult.validation_stage.passed}，失败 ${processResult.validation_stage.failed}`,
      },
    ];

    return (
      <Steps
        current={3}
        status={processResult.overall_success ? 'finish' : 'error'}
        items={steps}
      />
    );
  };

  const renderResultSummary = () => {
    if (!processResult) return null;

    return (
      <Row gutter={16}>
        <Col span={6}>
          <Statistic
            title="总文件数"
            value={processResult.total_files}
            prefix={<FileTextOutlined />}
          />
        </Col>
        <Col span={6}>
          <Statistic
            title="补全成功"
            value={processResult.completion_stage.successful}
            valueStyle={{ color: '#3f8600' }}
            prefix={<CheckCircleOutlined />}
          />
        </Col>
        <Col span={6}>
          <Statistic
            title="校验通过"
            value={processResult.validation_stage.passed}
            valueStyle={{ color: '#3f8600' }}
            prefix={<CheckCircleOutlined />}
          />
        </Col>
        <Col span={6}>
          <Statistic
            title="处理时间"
            value={processResult.processing_time}
            suffix="秒"
            prefix={<ClockCircleOutlined />}
          />
        </Col>
      </Row>
    );
  };

  const renderDetailedResults = () => {
    if (!processResult) return null;

    const completionColumns = [
      {
        title: '文件名',
        dataIndex: 'filename',
        key: 'filename',
      },
      {
        title: '状态',
        dataIndex: 'success',
        key: 'success',
        render: (success: boolean) => (
          <Tag color={success ? 'green' : 'red'}>
            {success ? '成功' : '失败'}
          </Tag>
        ),
      },
      {
        title: '操作',
        key: 'action',
        render: (_: any, record: any) => (
          <Space>
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={() => showInvoiceDetail(record)}
            >
              查看详情
            </Button>
          </Space>
        ),
      },
    ];

    return (
      <Collapse>
        <Panel header="补全阶段详情" key="completion">
          <Table
            dataSource={processResult.completion_stage.results}
            columns={completionColumns}
            pagination={false}
            size="small"
          />
        </Panel>
        <Panel header="合并阶段详情" key="merge">
          <List
            dataSource={processResult.merge_stage.merged_invoices}
            renderItem={(item: any, index: number) => (
              <List.Item>
                <List.Item.Meta
                  title={`合并发票 ${index + 1}`}
                  description={`包含 ${item.source_files?.length || 1} 个原始文件`}
                />
                <Button
                  type="link"
                  icon={<EyeOutlined />}
                  onClick={() => showInvoiceDetail(item)}
                >
                  查看详情
                </Button>
              </List.Item>
            )}
          />
        </Panel>
        <Panel header="校验阶段详情" key="validation">
          <Table
            dataSource={processResult.validation_stage.results}
            columns={[
              {
                title: '发票ID',
                dataIndex: 'invoice_id',
                key: 'invoice_id',
              },
              {
                title: '校验状态',
                dataIndex: 'success',
                key: 'success',
                render: (success: boolean) => (
                  <Tag color={success ? 'green' : 'red'}>
                    {success ? '通过' : '失败'}
                  </Tag>
                ),
              },
              {
                title: '操作',
                key: 'action',
                render: (_: any, record: any) => (
                  <Button
                    type="link"
                    icon={<EyeOutlined />}
                    onClick={() => showInvoiceDetail(record)}
                  >
                    查看详情
                  </Button>
                ),
              },
            ]}
            pagination={false}
            size="small"
          />
        </Panel>
      </Collapse>
    );
  };

  return (
    <div>
      <Title level={2} style={{ textAlign: 'center', marginBottom: 24 }}>
        📦 批量发票处理
        <div style={{ fontSize: '16px', color: '#666', fontWeight: 'normal', marginTop: 8 }}>
          支持多文件并发处理、智能合并和批量校验
        </div>
      </Title>

      {/* 文件上传区域 */}
      <Card title="文件上传" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Upload.Dragger {...uploadProps}>
            <p className="ant-upload-drag-icon">
              <UploadOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽XML文件到此区域上传</p>
            <p className="ant-upload-hint">
              支持批量上传，最多50个文件。支持.xml格式的KDUBL发票文件。
            </p>
          </Upload.Dragger>
          
          {fileList.length > 0 && (
            <div>
              <Divider orientation="left">已上传文件 ({fileList.length})</Divider>
              {renderFileList()}
            </div>
          )}
        </Space>
      </Card>

      {/* 处理配置 */}
      <Card title="处理配置" style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            系统将自动处理每个XML文件，支持单张发票的明细拆分功能。
            例如：包含住宿费、餐饮费、停车费的发票可自动拆分为不同类型的专票、普票等。
          </Text>
        </div>
        
        <Space>
          <Tag color="blue">并发处理</Tag>
          <Tag color="green">自动校验</Tag>
          <Tag color="orange">错误隔离</Tag>
          <Tag color="purple">明细拆分</Tag>
        </Space>
        
        <Button
          type="primary"
          size="large"
          onClick={handleBatchProcess}
          loading={loading}
          disabled={fileList.length === 0}
          icon={<MergeOutlined />}
          style={{ width: '100%' }}
        >
          {loading ? `处理中... (${processingProgress}%)` : '开始批量处理'}
        </Button>
      </Card>

      {/* 处理进度 */}
      {loading && (
        <Card title="处理进度" style={{ marginBottom: 16 }}>
          <Progress percent={processingProgress} status="active" />
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Text type="secondary">{currentStage}</Text>
          </div>
        </Card>
      )}

      {/* 处理结果 */}
      {processResult && (
        <Spin spinning={loading}>
          <Card title="处理结果" style={{ marginBottom: 16 }}>
            <Alert
              message={processResult.overall_success ? '批量处理成功' : '批量处理完成（部分失败）'}
              type={processResult.overall_success ? 'success' : 'warning'}
              showIcon
              style={{ marginBottom: 16 }}
              description={`批次ID: ${processResult.batch_id}`}
            />

            {renderProcessingSteps()}
            
            <Divider />
            
            {renderResultSummary()}
            
            <Divider />
            
            {renderDetailedResults()}
          </Card>
        </Spin>
      )}

      {/* 详情模态框 */}
      <Modal
        title="发票详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>,
        ]}
        width={1000}
      >
        {selectedInvoiceDetail && (
          <div>
            <Alert
              message={selectedInvoiceDetail.success ? '校验通过' : '校验失败'}
              type={selectedInvoiceDetail.success ? 'success' : 'error'}
              showIcon
              style={{ marginBottom: 16 }}
            />
            
            {selectedInvoiceDetail.errors && selectedInvoiceDetail.errors.length > 0 && (
              <Alert
                message="错误信息"
                description={
                  <ul>
                    {selectedInvoiceDetail.errors.map((error: string, index: number) => (
                      <li key={index}>{error}</li>
                    ))}
                  </ul>
                }
                type="error"
                style={{ marginBottom: 16 }}
              />
            )}
            
            {/* 显示校验规则执行详情 */}
            {selectedInvoiceDetail.validation_logs && selectedInvoiceDetail.validation_logs.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <h4>业务校验规则执行详情</h4>
                <div className="execution-logs">
                  {selectedInvoiceDetail.validation_logs.map((log: any, logIndex: number) => (
                    <div key={logIndex} className={`log-item ${
                      log.status === 'success' || log.status === 'passed' ? 'log-success' : 
                      log.status === 'skipped' ? 'log-skipped' :
                      log.status === 'failed' ? 'log-warning' : 'log-error'
                    }`} style={{ padding: '8px', marginBottom: '4px', borderLeft: '3px solid', borderLeftColor: log.status === 'passed' ? '#52c41a' : log.status === 'failed' ? '#ff4d4f' : '#faad14' }}>
                      <span className="log-icon">
                        {log.status === 'success' || log.status === 'passed' ? '✅' : 
                         log.status === 'skipped' ? '⏭️' :
                         log.status === 'failed' ? '❌' : '❓'}
                      </span>
                      <span className="log-message" style={{ marginLeft: 8 }}>
                        <strong>{log.rule_name}</strong>
                        <span style={{ marginLeft: 8, fontSize: '12px', color: '#999' }}>
                          ({log.status})
                        </span>
                        {/* 失败时显示详细错误消息 */}
                        {log.status === 'failed' && log.error_message && (
                          <span style={{ marginLeft: 8, color: '#ff4d4f' }}>
                            → {log.error_message}
                          </span>
                        )}
                        {/* 非失败状态的错误消息保持原样 */}
                        {log.status !== 'failed' && log.error_message && (
                          <span style={{ marginLeft: 8, color: '#faad14' }}>
                            ({log.error_message})
                          </span>
                        )}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
            
            <div>
              <h4>完整数据</h4>
              <pre style={{ maxHeight: '300px', overflow: 'auto', fontSize: '12px' }}>
                {JSON.stringify(selectedInvoiceDetail, null, 2)}
              </pre>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default BatchInvoiceProcessor;