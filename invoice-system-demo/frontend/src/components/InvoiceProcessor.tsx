import React, { useState, useEffect } from 'react';
import { Card, Button, Upload, message, Spin, Tabs, Alert, Typography, Space, Tag, Steps, Input, Checkbox, Row, Col, Statistic, Table } from 'antd';
import { UploadOutlined, FileTextOutlined, CheckCircleOutlined, CloseCircleOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { UploadProps, UploadFile } from 'antd';
import { invoiceService } from '../services/api';

const { TextArea } = Input;
const { Title, Text } = Typography;

const InvoiceProcessor: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [xmlContent, setXmlContent] = useState('');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [processResult, setProcessResult] = useState<any>(null);
  const [sampleFiles, setSampleFiles] = useState<any[]>([]);
  const [selectedSamples, setSelectedSamples] = useState<string[]>([]);
  const [complianceResult, setComplianceResult] = useState<any>(null);
  const [inputMode, setInputMode] = useState<'text' | 'files'>('text');

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
      
      if (inputMode === 'text') {
        // 文本模式：追加到现有内容
        if (xmlContent) {
          setXmlContent(xmlContent + '\n\n<!-- ' + filename + ' -->\n' + text);
        } else {
          setXmlContent(text);
        }
      }
      
      message.success(`已加载示例文件: ${filename}`);
    } catch (error) {
      message.error('加载示例文件失败');
    }
  };

  const loadSelectedSamples = async () => {
    if (selectedSamples.length === 0) {
      message.warning('请先选择示例文件');
      return;
    }

    try {
      let combinedContent = '';
      for (const filename of selectedSamples) {
        const response = await fetch(`/data/${filename}`);
        const text = await response.text();
        if (combinedContent) {
          combinedContent += '\n\n<!-- ' + filename + ' -->\n' + text;
        } else {
          combinedContent = '<!-- ' + filename + ' -->\n' + text;
        }
      }
      setXmlContent(combinedContent);
      message.success(`已加载 ${selectedSamples.length} 个示例文件`);
    } catch (error) {
      message.error('加载示例文件失败');
    }
  };

  const handleProcess = async () => {
    if (inputMode === 'text' && !xmlContent) {
      message.warning('请先输入或加载KDUBL数据');
      return;
    }
    
    if (inputMode === 'files' && fileList.length === 0) {
      message.warning('请先上传文件');
      return;
    }

    setLoading(true);
    try {
      let response;
      
      if (inputMode === 'files') {
        // 文件模式：使用文件上传接口
        const formData = new FormData();
        fileList.forEach((file) => {
          if (file.originFileObj) {
            formData.append('files', file.originFileObj);
          }
        });
        formData.append('source_system', 'ERP');
        formData.append('merge_strategy', 'none');
        
        response = await invoiceService.processInvoiceFiles(formData);
      } else {
        // 文本模式：使用JSON接口，支持多个XML内容
        const xmlList = xmlContent.split('<!-- ').filter(part => part.trim()).map(part => {
          // 移除文件名注释，保留XML内容
          const xmlStart = part.indexOf('-->');
          return xmlStart > -1 ? part.substring(xmlStart + 3).trim() : part.trim();
        }).filter(xml => xml);
        
        if (xmlList.length === 1) {
          response = await invoiceService.processInvoices({
            kdubl_xml: xmlList[0],
            source_system: 'ERP'
          });
        } else {
          response = await invoiceService.processInvoices({
            kdubl_list: xmlList,
            source_system: 'ERP',
            merge_strategy: 'none'
          });
        }
      }
      
      setProcessResult(response.data);
      
      if (response.data.success || response.data.overall_success) {
        message.success('发票处理成功');
        // 自动进行合规性校验
        if (inputMode === 'text') {
          handleComplianceCheck();
        }
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
    multiple: true,
    fileList: fileList,
    beforeUpload: (file) => {
      if (inputMode === 'text') {
        // 文本模式：读取文件内容
        const reader = new FileReader();
        reader.onload = (e) => {
          const content = e.target?.result as string;
          if (xmlContent) {
            setXmlContent(xmlContent + '\n\n<!-- ' + file.name + ' -->\n' + content);
          } else {
            setXmlContent('<!-- ' + file.name + ' -->\n' + content);
          }
          message.success(`${file.name} 文件内容已添加`);
        };
        reader.readAsText(file);
      } else {
        // 文件模式：添加到文件列表
        setFileList(prev => [...prev, file]);
        message.success(`${file.name} 文件已添加`);
      }
      return false;
    },
    onRemove: (file) => {
      setFileList(prev => prev.filter(item => item.uid !== file.uid));
    },
  };

  const renderProcessSteps = () => {
    if (!processResult) return null;

    // 渲染规则执行日志的通用函数
    const renderExecutionLogs = (logs: any[], title: string) => {
      if (!logs || logs.length === 0) return null;
      
      return (
        <Card title={title} size="small" style={{ marginTop: 16 }}>
          <div className="execution-logs">
            {logs.map((log: any, logIndex: number) => (
              <div key={logIndex} className={`log-item ${
                log.status === 'success' || log.status === 'passed' ? 'log-success' : 
                log.status === 'skipped' ? 'log-skipped' :
                log.status === 'failed' ? 'log-warning' : 'log-error'
              }`} style={{ 
                padding: '8px 12px', 
                marginBottom: '4px', 
                borderLeft: `3px solid ${
                  log.status === 'success' || log.status === 'passed' ? '#52c41a' : 
                  log.status === 'skipped' ? '#faad14' :
                  log.status === 'failed' ? '#ff7875' : '#ff4d4f'
                }`,
                backgroundColor: `${
                  log.status === 'success' || log.status === 'passed' ? '#f6ffed' : 
                  log.status === 'skipped' ? '#fffbe6' :
                  log.status === 'failed' ? '#fff2f0' : '#fff1f0'
                }`,
                borderRadius: '4px'
              }}>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <span className="log-icon" style={{ marginRight: '8px', fontSize: '14px' }}>
                    {log.status === 'success' || log.status === 'passed' ? '✅' : 
                     log.status === 'skipped' ? '⏭️' :
                     log.status === 'failed' ? '❌' : '❓'}
                  </span>
                  <span className="log-message" style={{ flex: 1 }}>
                    <strong>{log.rule_name}</strong>
                    <Tag 
                      color={
                        log.status === 'success' || log.status === 'passed' ? 'green' : 
                        log.status === 'skipped' ? 'orange' :
                        log.status === 'failed' ? 'red' : 'red'
                      }
                      style={{ marginLeft: 8, fontSize: '11px' }}
                    >
                      {log.status}
                    </Tag>
                    {/* 成功时显示设置的字段和值 */}
                    {log.status === 'success' && log.target_field && log.value && (
                      <span style={{ marginLeft: 8, color: '#666', fontSize: '12px' }}>
                        → {log.item_index !== undefined ? 
                            log.target_field.replace('items[]', `items[${log.item_index}]`) : 
                            log.target_field} = <code style={{ backgroundColor: '#f5f5f5', padding: '1px 4px' }}>{log.value}</code>
                      </span>
                    )}
                    {/* 跳过时显示原因 */}
                    {log.status === 'skipped' && log.reason && (
                      <span style={{ marginLeft: 8, color: '#999', fontSize: '12px' }}>
                        ({log.reason === 'condition_not_met' ? '条件不满足' : 
                          log.reason === 'inactive' ? '规则未激活' : log.reason})
                      </span>
                    )}
                    {/* 失败时显示错误消息 */}
                    {(log.status === 'failed' || log.status === 'error') && log.error && (
                      <div style={{ marginTop: 4, color: '#ff4d4f', fontSize: '12px' }}>
                        错误: {log.error}
                      </div>
                    )}
                  </span>
                </div>
                {/* 显示条件表达式 */}
                {log.condition && (
                  <div style={{ marginTop: 4, marginLeft: 22, fontSize: '11px', color: '#999', fontFamily: 'monospace', backgroundColor: '#fafafa', padding: '2px 6px', borderRadius: '2px' }}>
                    条件: {log.condition}
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>
      );
    };

    // 批量处理结果
    if (processResult.batch_id) {
      return (
        <div>
          <Card title="批量处理概览" style={{ marginBottom: 16 }}>
            <Row gutter={16}>
              <Col span={6}>
                <Statistic
                  title="总文件数"
                  value={processResult.summary?.total_inputs || processResult.file_mapping?.length || 0}
                  prefix={<FileTextOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="处理成功"
                  value={processResult.summary?.successful_inputs || 0}
                  valueStyle={{ color: '#3f8600' }}
                  prefix={<CheckCircleOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="处理失败"
                  value={processResult.summary?.failed_inputs || 0}
                  valueStyle={{ color: '#cf1322' }}
                  prefix={<CloseCircleOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="处理时间"
                  value={processResult.processing_time || 0}
                  suffix="秒"
                />
              </Col>
            </Row>
          </Card>

          {processResult.file_mapping && (
            <Card title="文件处理详情">
              <Table
                dataSource={processResult.file_mapping.map((item: any, index: number) => ({
                  ...item,
                  key: index,
                  // 从 details 中获取对应的执行日志
                  completion_logs: processResult.details?.[index]?.execution_details?.completion_logs || [],
                  validation_logs: processResult.details?.[index]?.execution_details?.validation_logs || []
                }))}
                pagination={false}
                size="small"
                columns={[
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
                    title: '错误信息',
                    dataIndex: 'error',
                    key: 'error',
                    render: (error: string) => error || '-',
                  },
                ]}
                expandable={{
                  expandedRowRender: (record: any) => (
                    <div style={{ margin: 0 }}>
                      {/* 补全规则执行日志 */}
                      {record.completion_logs && record.completion_logs.length > 0 && 
                        renderExecutionLogs(record.completion_logs, '🔧 补全规则执行详情')}
                      
                      {/* 校验规则执行日志 */}
                      {record.validation_logs && record.validation_logs.length > 0 && 
                        renderExecutionLogs(record.validation_logs, '🔍 校验规则执行详情')}
                    </div>
                  ),
                  rowExpandable: (record: any) => 
                    (record.completion_logs && record.completion_logs.length > 0) ||
                    (record.validation_logs && record.validation_logs.length > 0),
                }}
              />
            </Card>
          )}

          {/* 规则执行详情 */}
          {processResult.execution_details && (
            <div>
              {/* 按文件分组的补全规则执行日志 */}
              {processResult.execution_details.completion_by_file && 
                processResult.execution_details.completion_by_file.length > 0 && (
                <Card 
                  title={`📝 补全规则执行详情 - 按文件分组 (${processResult.execution_details.completion_by_file.length} 个文件)`}
                  size="small" 
                  style={{ marginTop: 16 }}
                >
                  {processResult.execution_details.completion_by_file.map((fileLog: any, fileIndex: number) => (
                    <Card 
                      key={fileIndex}
                      type="inner"
                      title={
                        <span>
                          📄 文件 {fileIndex + 1}: {fileLog.file_name}
                          <Tag color="blue" style={{ marginLeft: 8 }}>
                            {fileLog.invoice_number}
                          </Tag>
                          <Tag color="green" style={{ marginLeft: 4 }}>
                            {fileLog.completion_logs?.length || 0} 条规则
                          </Tag>
                        </span>
                      }
                      size="small"
                      style={{ marginBottom: 12 }}
                    >
                      {fileLog.completion_logs && fileLog.completion_logs.length > 0 ? (
                        <div className="execution-logs">
                          {fileLog.completion_logs.map((log: any, logIndex: number) => (
                            <div key={logIndex} className={`log-item ${
                              log.status === 'success' || log.status === 'passed' ? 'log-success' : 
                              log.status === 'skipped' ? 'log-skipped' :
                              log.status === 'failed' ? 'log-warning' : 'log-error'
                            }`} style={{ 
                              padding: '8px 12px', 
                              marginBottom: '4px', 
                              borderLeft: `3px solid ${
                                log.status === 'success' || log.status === 'passed' ? '#52c41a' : 
                                log.status === 'skipped' ? '#faad14' :
                                log.status === 'failed' ? '#ff7875' : '#ff4d4f'
                              }`,
                              backgroundColor: log.status === 'success' || log.status === 'passed' ? '#f6ffed' : 
                                             log.status === 'skipped' ? '#fffbe6' :
                                             log.status === 'failed' ? '#fff2f0' : '#fff1f0',
                              borderRadius: '4px',
                              display: 'flex',
                              alignItems: 'flex-start'
                            }}>
                              <span className="log-icon" style={{ marginRight: 8, fontSize: '14px' }}>
                                {log.status === 'success' || log.status === 'passed' ? '✅' : 
                                 log.status === 'skipped' ? '⏭️' :
                                 log.status === 'failed' ? '❌' : '❓'}
                              </span>
                              <span className="log-message" style={{ flex: 1 }}>
                                <strong>{log.rule_name}</strong>
                                <Tag 
                                  color={
                                    log.status === 'success' || log.status === 'passed' ? 'green' : 
                                    log.status === 'skipped' ? 'orange' :
                                    log.status === 'failed' ? 'red' : 'red'
                                  }
                                  style={{ marginLeft: 8, fontSize: '11px' }}
                                >
                                  {log.status}
                                </Tag>
                                {/* 成功时显示设置的字段和值 */}
                                {log.status === 'success' && log.target_field && log.value && (
                                  <div style={{ marginTop: 4, color: '#52c41a', fontSize: '12px' }}>
                                    {log.target_field}: <strong>{log.value}</strong>
                                  </div>
                                )}
                                {/* 跳过时显示原因 */}
                                {log.status === 'skipped' && log.reason && (
                                  <span style={{ marginLeft: 8, color: '#faad14', fontSize: '12px' }}>
                                    ({log.reason === 'condition_not_met' ? '条件不满足' : 
                                      log.reason === 'inactive' ? '规则未激活' : log.reason})
                                  </span>
                                )}
                                {/* 失败时显示错误消息 */}
                                {(log.status === 'failed' || log.status === 'error') && log.error && (
                                  <div style={{ marginTop: 4, color: '#ff4d4f', fontSize: '12px' }}>
                                    错误: {log.error}
                                  </div>
                                )}
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div style={{ color: '#999', fontStyle: 'italic' }}>该文件无补全规则执行记录</div>
                      )}
                    </Card>
                  ))}
                </Card>
              )}
              
              {/* 按发票分组的验证规则执行日志 */}
              {processResult.execution_details.validation_by_invoice && 
                processResult.execution_details.validation_by_invoice.length > 0 && (
                <Card 
                  title={`🔍 验证规则执行详情 - 按发票分组 (${processResult.execution_details.validation_by_invoice.length} 张发票)`}
                  size="small" 
                  style={{ marginTop: 16 }}
                >
                  {processResult.execution_details.validation_by_invoice.map((invoiceLog: any, invoiceIndex: number) => (
                    <Card 
                      key={invoiceIndex}
                      type="inner"
                      title={
                        <span>
                          🧾 发票 {invoiceIndex + 1}: {invoiceLog.invoice_number}
                          <Tag color="purple" style={{ marginLeft: 8 }}>
                            {invoiceLog.validation_logs?.length || 0} 条规则
                          </Tag>
                        </span>
                      }
                      size="small"
                      style={{ marginBottom: 12 }}
                    >
                      {invoiceLog.validation_logs && invoiceLog.validation_logs.length > 0 ? (
                        <div className="execution-logs">
                          {invoiceLog.validation_logs.map((log: any, logIndex: number) => (
                            <div key={logIndex} className={`log-item ${
                              log.status === 'success' || log.status === 'passed' ? 'log-success' : 
                              log.status === 'skipped' ? 'log-skipped' :
                              log.status === 'failed' ? 'log-warning' : 'log-error'
                            }`} style={{ 
                              padding: '8px 12px', 
                              marginBottom: '4px', 
                              borderLeft: `3px solid ${
                                log.status === 'success' || log.status === 'passed' ? '#52c41a' : 
                                log.status === 'skipped' ? '#faad14' :
                                log.status === 'failed' ? '#ff7875' : '#ff4d4f'
                              }`,
                              backgroundColor: log.status === 'success' || log.status === 'passed' ? '#f6ffed' : 
                                             log.status === 'skipped' ? '#fffbe6' :
                                             log.status === 'failed' ? '#fff2f0' : '#fff1f0',
                              borderRadius: '4px',
                              display: 'flex',
                              alignItems: 'flex-start'
                            }}>
                              <span className="log-icon" style={{ marginRight: 8, fontSize: '14px' }}>
                                {log.status === 'success' || log.status === 'passed' ? '✅' : 
                                 log.status === 'skipped' ? '⏭️' :
                                 log.status === 'failed' ? '❌' : '❓'}
                              </span>
                              <span className="log-message" style={{ flex: 1 }}>
                                <strong>{log.rule_name}</strong>
                                <Tag 
                                  color={
                                    log.status === 'success' || log.status === 'passed' ? 'green' : 
                                    log.status === 'skipped' ? 'orange' :
                                    log.status === 'failed' ? 'red' : 'red'
                                  }
                                  style={{ marginLeft: 8, fontSize: '11px' }}
                                >
                                  {log.status}
                                </Tag>
                                {/* 验证规则的详细信息 */}
                                {log.message && (
                                  <div style={{ marginTop: 4, fontSize: '12px', color: '#666' }}>
                                    {log.message}
                                  </div>
                                )}
                                {/* 失败时显示错误消息 */}
                                {(log.status === 'failed' || log.status === 'error') && log.error && (
                                  <div style={{ marginTop: 4, color: '#ff4d4f', fontSize: '12px' }}>
                                    错误: {log.error}
                                  </div>
                                )}
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div style={{ color: '#999', fontStyle: 'italic' }}>该发票无验证规则执行记录</div>
                      )}
                    </Card>
                  ))}
                </Card>
              )}

              {/* 保留原有的全局日志展示作为备用 */}
              {(!processResult.execution_details.completion_by_file || processResult.execution_details.completion_by_file.length === 0) &&
                processResult.execution_details.completion_logs && 
                renderExecutionLogs(
                  processResult.execution_details.completion_logs, 
                  `📝 补全规则执行详情 (${processResult.execution_details.completion_logs.length} 条规则)`
                )}
              
              {(!processResult.execution_details.validation_by_invoice || processResult.execution_details.validation_by_invoice.length === 0) &&
                processResult.execution_details.validation_logs && 
                renderExecutionLogs(
                  processResult.execution_details.validation_logs, 
                  `🔍 校验规则执行详情 (${processResult.execution_details.validation_logs.length} 条规则)`
                )}
            </div>
          )}
        </div>
      );
    }

    // 单个处理结果（保持原有逻辑）
    if (!processResult?.steps) return null;

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
            {/* 在校验失败时也显示校验详情 */}
            {step.includes('校验失败') && processResult.execution_details?.validation_logs && 
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
          基于 Google CEL 引擎的智能规则处理系统 - 支持单张和批量处理
        </div>
      </Title>
      
      {/* 输入模式选择 */}
      <Card title="输入模式" style={{ marginBottom: 16 }}>
        <Space>
          <Button 
            type={inputMode === 'text' ? 'primary' : 'default'}
            onClick={() => setInputMode('text')}
          >
            文本输入模式
          </Button>
          <Button 
            type={inputMode === 'files' ? 'primary' : 'default'}
            onClick={() => setInputMode('files')}
          >
            文件上传模式
          </Button>
        </Space>
        <div style={{ marginTop: 8, color: '#666', fontSize: '12px' }}>
          {inputMode === 'text' ? '支持粘贴多个XML内容或加载示例文件' : '支持上传多个XML文件进行批量处理'}
        </div>
      </Card>
      
      {/* 示例文件 */}
      <Card title="示例数据" style={{ marginBottom: 16 }}>
        {inputMode === 'text' ? (
          <div>
            <div style={{ marginBottom: 16 }}>
              <Checkbox.Group 
                value={selectedSamples} 
                onChange={setSelectedSamples}
                style={{ width: '100%' }}
              >
                <Row>
                  {sampleFiles.map((file) => (
                    <Col span={8} key={file.filename} style={{ marginBottom: 8 }}>
                      <Checkbox value={file.filename}>
                        <FileTextOutlined style={{ marginRight: 4 }} />
                        {file.filename}
                      </Checkbox>
                    </Col>
                  ))}
                </Row>
              </Checkbox.Group>
            </div>
            <Space>
              <Button 
                icon={<PlusOutlined />}
                onClick={loadSelectedSamples}
                disabled={selectedSamples.length === 0}
              >
                加载选中的示例文件 ({selectedSamples.length})
              </Button>
              <Button 
                onClick={() => {
                  setXmlContent('');
                  setSelectedSamples([]);
                }}
              >
                清空内容
              </Button>
            </Space>
          </div>
        ) : (
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
        )}
      </Card>

      {/* 输入区域 */}
      <Card title={inputMode === 'text' ? 'KDUBL文本输入' : '文件上传'} style={{ marginBottom: 16 }}>
        <Upload {...uploadProps}>
          <Button icon={<UploadOutlined />}>
            {inputMode === 'text' ? '上传XML文件（内容会添加到文本框）' : '上传XML文件'}
          </Button>
        </Upload>
        
        {inputMode === 'text' && (
          <TextArea
            rows={12}
            value={xmlContent}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setXmlContent(e.target.value)}
            placeholder="粘贴KDUBL XML内容...&#10;&#10;支持多个XML内容，每个XML之间用注释分隔：&#10;<!-- filename1.xml -->&#10;<xml>...</xml>&#10;&#10;<!-- filename2.xml -->&#10;<xml>...</xml>"
            style={{ marginTop: 16 }}
            className="xml-viewer"
          />
        )}
        
        {inputMode === 'files' && fileList.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <Table
              dataSource={fileList}
              pagination={false}
              size="small"
              columns={[
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
              ]}
            />
          </div>
        )}
        
        <Button 
          type="primary" 
          onClick={handleProcess}
          loading={loading}
          style={{ marginTop: 16 }}
          disabled={inputMode === 'text' ? !xmlContent : fileList.length === 0}
        >
          {inputMode === 'text' ? '处理发票' : `批量处理 (${fileList.length} 个文件)`}
        </Button>
      </Card>

      {/* 处理结果 */}
      {processResult && (
        <Spin spinning={loading}>
          <Card title="处理结果" style={{ marginBottom: 16 }}>
            <Alert
              message={processResult.success || processResult.overall_success ? '处理成功' : '处理失败'}
              type={processResult.success || processResult.overall_success ? 'success' : 'error'}
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

            {/* 单个处理结果的详细数据 */}
            {processResult.success && processResult.data && processResult.data.results && processResult.data.results.length > 0 && (
              <Tabs defaultActiveKey="1" style={{ marginTop: 16 }}>
                <Tabs.TabPane tab="Domain Object" key="1">
                  <pre className="xml-viewer">
                    {JSON.stringify(processResult.data.results[0].domain_object, null, 2)}
                  </pre>
                </Tabs.TabPane>
                <Tabs.TabPane tab="处理后的KDUBL" key="2">
                  <pre className="xml-viewer">
                    {processResult.data.results[0].processed_kdubl}
                  </pre>
                </Tabs.TabPane>
              </Tabs>
            )}

            {/* 批量处理结果的详细数据 */}
            {processResult.batch_id && processResult.execution_logs && (
              <Card title="执行日志" style={{ marginTop: 16 }}>
                <div style={{ maxHeight: '400px', overflow: 'auto' }}>
                  {processResult.execution_logs.map((log: any, index: number) => (
                    <div key={index} style={{ 
                      padding: '8px', 
                      borderBottom: '1px solid #f0f0f0',
                      backgroundColor: log.level === 'ERROR' ? '#fff2f0' : 
                                     log.level === 'WARNING' ? '#fffbe6' : '#f6ffed'
                    }}>
                      <Tag color={log.level === 'ERROR' ? 'red' : log.level === 'WARNING' ? 'orange' : 'green'}>
                        {log.level}
                      </Tag>
                      <span style={{ marginLeft: 8 }}>{log.message}</span>
                      <span style={{ float: 'right', color: '#999', fontSize: '12px' }}>
                        {new Date(log.timestamp).toLocaleTimeString()}
                      </span>
                    </div>
                  ))}
                </div>
              </Card>
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