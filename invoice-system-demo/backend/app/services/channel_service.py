"""通道层服务 - Mock实现"""
from typing import Dict, List
import random
from datetime import datetime


class MockChannelService:
    """模拟的通道层服务"""
    
    def __init__(self):
        self.compliance_rules = {
            "CN": {
                "required_fields": ["invoice_number", "issue_date", "supplier.tax_no", "customer.name"],
                "tax_rates": [0.06, 0.09, 0.13],
                "invoice_types": ["380", "381", "386"]
            },
            "US": {
                "required_fields": ["invoice_number", "issue_date", "supplier.name", "customer.name"],
                "tax_rates": [0.05, 0.08, 0.10],
                "invoice_types": ["380", "381"]
            }
        }
    
    def validate_compliance(self, kdubl: str, country: str = "CN") -> Dict:
        """模拟合规性校验"""
        result = {
            "success": True,
            "compliance_status": "PASSED",
            "validation_type": "XSD|TAX_RULE|FORMAT",
            "errors": [],
            "warnings": [],
            "timestamp": datetime.now().isoformat()
        }
        
        # 模拟随机的合规性检查结果
        if random.random() > 0.8:  # 20%的概率失败
            result["success"] = False
            result["compliance_status"] = "FAILED"
            result["errors"] = [
                "发票格式不符合国家标准",
                f"税率必须是{self.compliance_rules[country]['tax_rates']}之一"
            ]
        elif random.random() > 0.7:  # 10%的概率有警告
            result["warnings"] = [
                "建议填写更详细的商品描述",
                "客户地址信息不完整"
            ]
        
        return result
    
    def deliver_invoice(self, invoice_data: Dict, delivery_channel: str = "email") -> Dict:
        """模拟发票交付"""
        result = {
            "success": True,
            "delivery_channel": delivery_channel,
            "delivery_status": "SENT",
            "tracking_id": f"TRACK-{datetime.now().strftime('%Y%m%d%H%M%S')}",
            "timestamp": datetime.now().isoformat()
        }
        
        # 模拟不同的交付渠道
        if delivery_channel == "email":
            result["recipient_info"] = "customer@example.com"
            result["delivery_details"] = {
                "email_sent": True,
                "smtp_response": "250 OK"
            }
        elif delivery_channel == "peppol":
            result["recipient_info"] = "0088:1234567890"
            result["delivery_details"] = {
                "peppol_id": "PEPPOL-2025-001",
                "network_status": "ACCEPTED"
            }
        elif delivery_channel == "api":
            result["recipient_info"] = "https://api.customer.com/invoices"
            result["delivery_details"] = {
                "http_status": 200,
                "response": "Invoice received"
            }
        
        # 模拟交付失败
        if random.random() > 0.9:  # 10%的概率失败
            result["success"] = False
            result["delivery_status"] = "FAILED"
            result["error"] = "Network timeout"
        
        return result
    
    def generate_pdf(self, invoice_data: Dict) -> Dict:
        """模拟PDF生成"""
        return {
            "success": True,
            "file_url": f"/static/invoices/INV-{invoice_data.get('invoice_number', 'UNKNOWN')}.pdf",
            "file_size": "256KB",
            "pages": 2,
            "generated_at": datetime.now().isoformat()
        }