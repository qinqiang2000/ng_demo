import React, { useState, useEffect } from 'react';
import { Card, Button, Upload, message, Spin, Tabs, Alert, Typography, Space, Tag, Steps, Input } from 'antd';
import { UploadOutlined, FileTextOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { invoiceService } from '../services/api';

const { TextArea } = Input;
const { Title, Text } = Typography;

const InvoiceProcessor: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [xmlContent, setXmlContent] = useState('');
  const [processResult, setProcessResult] = useState<any>(null);
  const [sampleFiles, setSampleFiles] = useState<any[]>([]);
  const [complianceResult, setComplianceResult] = useState<any>(null);

  useEffect(() => {
    loadSampleFiles();
  }, []);

  const loadSampleFiles = async () => {
    try {
      const response = await invoiceService.getSampleData();
      if (response.data.success) {
        setSampleFiles(response.data.data);
      }
    } catch (error) {
      console.error('Failed to load sample files:', error);
    }
  };

  const loadSampleFile = async (filename: string) => {
    try {
      const response = await fetch(`/data/${filename}`);
      const text = await response.text();
      setXmlContent(text);
      message.success(`已加载示例文件: ${filename}`);
    } catch (error) {
      message.error('加载示例文件失败');
    }
  };

  const handleProcess = async () => {
    if (!xmlContent) {
      message.warning('请先输入或上传KDUBL数据');
      return;
    }

    setLoading(true);
    try {
      const response = await invoiceService.processInvoice({
        kdubl_xml: xmlContent,
        source_system: 'ERP'
      });
      
      setProcessResult(response.data);
      
      if (response.data.success) {
        message.success('发票处理成功');
        // 自动进行合规性校验
        handleComplianceCheck();
      } else {
        message.error('发票处理失败');
      }
    } catch (error) {
      message.error('处理失败: ' + error);
    } finally {
      setLoading(false);
    }
  };

  const handleComplianceCheck = async () => {
    try {
      const response = await invoiceService.validateCompliance({
        kdubl_xml: xmlContent,
        source_system: 'ERP'
      });
      setComplianceResult(response.data);
    } catch (error) {
      console.error('合规性校验失败:', error);
    }
  };

  const uploadProps: UploadProps = {
    beforeUpload: (file) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        setXmlContent(e.target?.result as string);
        message.success(`${file.name} 文件上传成功`);
      };
      reader.readAsText(file);
      return false;
    },
  };

  const renderProcessSteps = () => {
    if (!processResult?.steps) return null;

    const renderExecutionLogs = (logs: any[], title: string) => {
      if (!logs || logs.length === 0) return null;
      
      return (
        <div className="execution-logs" style={{ marginTop: 8, marginLeft: 20 }}>
          {logs.map((log: any, logIndex: number) => (
            <div key={logIndex} className={`log-item ${
              log.status === 'success' || log.status === 'passed' ? 'log-success' : 
              log.status === 'skipped' ? 'log-skipped' :
              log.status === 'failed' ? 'log-warning' : 'log-error'
            }`}>
              <span className="log-icon">
                {log.status === 'success' || log.status === 'passed' ? '✅' : 
                 log.status === 'skipped' ? '⏭️' :
                 log.status === 'failed' ? '❌' : '❓'}
              </span>
              <span className="log-message">
                <strong>{log.rule_name}</strong>
                <span style={{ marginLeft: 8, fontSize: '12px', color: '#999' }}>
                  ({log.status})
                </span>
                {log.target_field && log.value && (
                  <span style={{ marginLeft: 8, color: '#666' }}>
                    → {log.item_index !== undefined ? 
                        log.target_field.replace('items[]', `items[${log.item_index}]`) : 
                        log.target_field} = {log.value}
                  </span>
                )}
                {log.reason && (
                  <span style={{ marginLeft: 8, color: '#999', fontSize: '12px' }}>
                    {log.reason === 'condition_not_met' ? '条件不满足' : 
                     log.reason === 'inactive' ? '规则未激活' : log.reason}
                  </span>
                )}
                {log.condition && (
                  <div style={{ marginLeft: 20, fontSize: '11px', color: '#ccc', fontFamily: 'monospace' }}>
                    条件: {log.condition}
                  </div>
                )}
                {log.error_message && (
                  <span style={{ marginLeft: 8, color: '#faad14' }}>
                    ({log.error_message})
                  </span>
                )}
              </span>
            </div>
          ))}
        </div>
      );
    };

    return (
      <Card 
        title={
          <span>
            🚀 处理步骤 
            <span style={{ marginLeft: 8, fontSize: '12px', color: '#1890ff', fontWeight: 'normal' }}>
              (Google CEL 引擎驱动)
            </span>
          </span>
        } 
        className="steps-card"
      >
        {processResult.steps.map((step: string, index: number) => (
          <div key={index}>
            <div className={`step-item ${step.startsWith('✓') ? 'step-success' : step.startsWith('✗') ? 'step-error' : ''}`}>
              {step}
            </div>
            {/* 在"数据补全完成"后显示补全详情 */}
            {step === '✓ 数据补全完成' && processResult.execution_details?.completion_logs && 
              renderExecutionLogs(processResult.execution_details.completion_logs, '补全规则执行详情')}
            {/* 在"业务校验通过"后显示校验详情 */}
            {step === '✓ 业务校验通过' && processResult.execution_details?.validation_logs && 
              renderExecutionLogs(processResult.execution_details.validation_logs, '校验规则执行详情')}
          </div>
        ))}
      </Card>
    );
  };

  return (
    <div>
      <Title level={2} style={{ textAlign: 'center', marginBottom: 24 }}>
        🚀 下一代发票系统 MVP Demo
        <div style={{ fontSize: '16px', color: '#666', fontWeight: 'normal', marginTop: 8 }}>
          基于 Google CEL 引擎的智能规则处理系统
        </div>
      </Title>
      
      {/* 示例文件 */}
      <Card title="示例数据" style={{ marginBottom: 16 }}>
        <Space>
          {sampleFiles.map((file) => (
            <Button 
              key={file.filename}
              icon={<FileTextOutlined />}
              onClick={() => loadSampleFile(file.filename)}
            >
              {file.filename}
            </Button>
          ))}
        </Space>
      </Card>

      {/* 输入区域 */}
      <Card title="KDUBL输入" style={{ marginBottom: 16 }}>
        <Upload {...uploadProps}>
          <Button icon={<UploadOutlined />}>上传XML文件</Button>
        </Upload>
        
        <TextArea
          rows={10}
          value={xmlContent}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setXmlContent(e.target.value)}
          placeholder="粘贴KDUBL XML内容..."
          style={{ marginTop: 16 }}
          className="xml-viewer"
        />
        
        <Button 
          type="primary" 
          onClick={handleProcess}
          loading={loading}
          style={{ marginTop: 16 }}
          disabled={!xmlContent}
        >
          处理发票
        </Button>
      </Card>

      {/* 处理结果 */}
      {processResult && (
        <Spin spinning={loading}>
          <Card title="处理结果" style={{ marginBottom: 16 }}>
            <Alert
              message={processResult.success ? '处理成功' : '处理失败'}
              type={processResult.success ? 'success' : 'error'}
              showIcon
              style={{ marginBottom: 16 }}
            />
            
            {processResult.errors && processResult.errors.length > 0 && (
              <Alert
                message="错误信息"
                description={
                  <ul>
                    {processResult.errors.map((error: string, index: number) => (
                      <li key={index}>{error}</li>
                    ))}
                  </ul>
                }
                type="error"
                style={{ marginBottom: 16 }}
              />
            )}

            {renderProcessSteps()}

            {processResult.success && processResult.data && (
              <Tabs defaultActiveKey="1" style={{ marginTop: 16 }}>
                <Tabs.TabPane tab="Domain Object" key="1">
                  <pre className="xml-viewer">
                    {JSON.stringify(processResult.data.domain_object, null, 2)}
                  </pre>
                </Tabs.TabPane>
                <Tabs.TabPane tab="处理后的KDUBL" key="2">
                  <pre className="xml-viewer">
                    {processResult.data.processed_kdubl}
                  </pre>
                </Tabs.TabPane>
              </Tabs>
            )}
          </Card>
        </Spin>
      )}

      {/* 合规性校验结果 */}
      {complianceResult && (
        <Card title="合规性校验结果（模拟通道层）">
          <Alert
            message={complianceResult.compliance_status}
            type={complianceResult.success ? 'success' : 'error'}
            showIcon
            description={
              <div>
                <p>校验类型: {complianceResult.validation_type}</p>
                <p>时间: {complianceResult.timestamp}</p>
                {complianceResult.errors && complianceResult.errors.length > 0 && (
                  <div>
                    <p>错误:</p>
                    <ul>
                      {complianceResult.errors.map((error: string, index: number) => (
                        <li key={index}>{error}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {complianceResult.warnings && complianceResult.warnings.length > 0 && (
                  <div>
                    <p>警告:</p>
                    <ul>
                      {complianceResult.warnings.map((warning: string, index: number) => (
                        <li key={index}>{warning}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            }
          />
        </Card>
      )}
    </div>
  );
};

export default InvoiceProcessor;