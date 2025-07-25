import axios from 'axios';

const API_BASE_URL = '/api';

export const invoiceService = {
  // 统一发票处理 - JSON格式（支持单张和批量）
  processInvoices: (data: { 
    kdubl_xml?: string; 
    kdubl_list?: string[];
    source_system: string;
    merge_strategy?: string;
    merge_config?: any;
  }) => {
    return axios.post(`${API_BASE_URL}/invoice/process`, data);
  },

  // 统一发票处理 - 文件上传（支持单张和批量）
  processInvoiceFiles: (formData: FormData) => {
    return axios.post(`${API_BASE_URL}/invoice/process-files`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  // 合规性校验
  validateCompliance: (data: { 
    kdubl_xml?: string; 
    kdubl_list?: string[];
    source_system: string;
  }) => {
    return axios.post(`${API_BASE_URL}/invoice/validate-compliance`, data);
  },

  // 获取规则
  getRules: () => {
    return axios.get(`${API_BASE_URL}/rules`);
  },

  // 获取连接器
  getConnectors: () => {
    return axios.get(`${API_BASE_URL}/connectors`);
  },

  // 获取示例数据
  getSampleData: () => {
    return axios.get(`${API_BASE_URL}/sample-data`);
  },

  // 交付发票
  deliverInvoice: (data: { invoice_data: any; delivery_channel: string }) => {
    return axios.post(`${API_BASE_URL}/invoice/deliver`, data);
  },

  // 兼容性方法 - 单张发票处理（内部调用统一接口）
  processInvoice: (data: { kdubl_xml: string; source_system: string }) => {
    return invoiceService.processInvoices({
      kdubl_xml: data.kdubl_xml,
      source_system: data.source_system,
      merge_strategy: 'none'
    });
  },

  // 兼容性方法 - 批量发票处理（内部调用统一接口）
  processBatchInvoices: (formData: FormData) => {
    return invoiceService.processInvoiceFiles(formData);
  }
};

export const dataApi = {
  // 企业管理
  getCompanies: (params?: any) => 
    axios.get(`${API_BASE_URL}/data/companies`, { params }).then(res => res.data),
  
  getCompany: (id: number) => 
    axios.get(`${API_BASE_URL}/data/companies/${id}`).then(res => res.data),
  
  getCompanyByTaxNumber: (taxNumber: string) => 
    axios.get(`${API_BASE_URL}/data/companies/by-tax-number/${taxNumber}`).then(res => res.data),
  
  createCompany: (data: any) => 
    axios.post(`${API_BASE_URL}/data/companies`, data).then(res => res.data),
  
  updateCompany: (id: number, data: any) => 
    axios.put(`${API_BASE_URL}/data/companies/${id}`, data).then(res => res.data),
  
  deleteCompany: (id: number) => 
    axios.delete(`${API_BASE_URL}/data/companies/${id}`).then(res => res.data),

  // 税率管理
  getTaxRates: (params?: any) => 
    axios.get(`${API_BASE_URL}/data/tax-rates`, { params }).then(res => res.data),
  
  getTaxRate: (id: number) => 
    axios.get(`${API_BASE_URL}/data/tax-rates/${id}`).then(res => res.data),
  
  getTaxRateByCategoryAmount: (category: string, amount: number) => 
    axios.get(`${API_BASE_URL}/data/tax-rates/by-category-amount`, { 
      params: { category, amount } 
    }).then(res => res.data),
  
  createTaxRate: (data: any) => 
    axios.post(`${API_BASE_URL}/data/tax-rates`, data).then(res => res.data),
  
  updateTaxRate: (id: number, data: any) => 
    axios.put(`${API_BASE_URL}/data/tax-rates/${id}`, data).then(res => res.data),
  
  deleteTaxRate: (id: number) => 
    axios.delete(`${API_BASE_URL}/data/tax-rates/${id}`).then(res => res.data),

  // 统计信息
  getStats: () => 
    axios.get(`${API_BASE_URL}/data/stats`).then(res => res.data)
};