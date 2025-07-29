"""KDUBL与Domain Object转换器"""
from lxml import etree
from decimal import Decimal
from datetime import datetime
from typing import Optional, List, Dict, Any
from ..models.domain import InvoiceDomainObject, Party, Address, InvoiceItem


class KDUBLDomainConverter:
    """KDUBL与Domain Object转换器 - 纯内存操作，仅在业务处理时使用"""
    
    # UBL命名空间
    NAMESPACES = {
        'cbc': 'urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2',
        'cac': 'urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2'
    }
    
    def parse(self, kdubl_xml: str) -> InvoiceDomainObject:
        """KDUBL -> Domain Object，用于业务规则处理前的数据准备"""
        doc = etree.fromstring(kdubl_xml.encode('utf-8'))
        
        # 提取基础信息
        invoice_number = self._extract_text(doc, './/cbc:ID') or f"INV-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        issue_date_str = self._extract_text(doc, './/cbc:IssueDate')
        if issue_date_str:
            issue_date = datetime.strptime(issue_date_str, '%Y-%m-%d').date()
        else:
            # 如果没有找到日期，使用今天的日期作为默认值
            issue_date = datetime.now().date()
        invoice_type = self._extract_text(doc, './/cbc:InvoiceTypeCode') or "STANDARD"
        
        # 提取参与方信息
        supplier = self._extract_party(doc, './/cac:AccountingSupplierParty')
        customer = self._extract_party(doc, './/cac:AccountingCustomerParty')
        
        # 提取商品明细
        items = self._extract_items(doc)
        
        # 提取金额信息
        total_amount = self._extract_amount(doc, './/cac:LegalMonetaryTotal/cbc:PayableAmount')
        tax_amount = self._extract_amount(doc, './/cac:TaxTotal/cbc:TaxAmount')
        net_amount = self._extract_amount(doc, './/cac:LegalMonetaryTotal/cbc:LineExtensionAmount')
        
        return InvoiceDomainObject(
            invoice_number=invoice_number,
            issue_date=issue_date,
            invoice_type=invoice_type,
            supplier=supplier,
            customer=customer,
            items=items,
            total_amount=total_amount or Decimal('0'),
            tax_amount=tax_amount,
            net_amount=net_amount
        )
    
    def build(self, domain: InvoiceDomainObject) -> str:
        """Domain Object -> KDUBL，业务规则处理后生成标准格式"""
        # 创建根元素
        root = etree.Element('Invoice', nsmap=self.NAMESPACES)
        
        # 基础信息
        self._add_element(root, 'cbc:UBLVersionID', '2.1')
        self._add_element(root, 'cbc:ID', domain.invoice_number)
        self._add_element(root, 'cbc:IssueDate', domain.issue_date.strftime('%Y-%m-%d'))
        self._add_element(root, 'cbc:InvoiceTypeCode', domain.invoice_type)
        self._add_element(root, 'cbc:DocumentCurrencyCode', 'CNY')
        
        # 参与方信息
        self._add_party(root, 'cac:AccountingSupplierParty', domain.supplier)
        self._add_party(root, 'cac:AccountingCustomerParty', domain.customer)
        
        # 商品明细
        for item in domain.items:
            self._add_invoice_line(root, item)
        
        # 金额信息
        monetary_total = etree.SubElement(root, '{%s}LegalMonetaryTotal' % self.NAMESPACES['cac'])
        if domain.net_amount:
            self._add_amount(monetary_total, 'cbc:LineExtensionAmount', domain.net_amount)
        if domain.tax_amount:
            tax_total = etree.SubElement(root, '{%s}TaxTotal' % self.NAMESPACES['cac'])
            self._add_amount(tax_total, 'cbc:TaxAmount', domain.tax_amount)
        self._add_amount(monetary_total, 'cbc:PayableAmount', domain.total_amount)
        
        return etree.tostring(root, pretty_print=True, encoding='unicode')
    
    def _extract_text(self, element, xpath: str) -> Optional[str]:
        """提取文本内容"""
        result = element.xpath(xpath, namespaces=self.NAMESPACES)
        return result[0].text if result and result[0].text else None
    
    def _extract_amount(self, element, xpath: str) -> Optional[Decimal]:
        """提取金额"""
        text = self._extract_text(element, xpath)
        return Decimal(text) if text else None
    
    def _extract_party(self, doc, xpath: str) -> Party:
        """提取参与方信息"""
        party_elem = doc.xpath(xpath, namespaces=self.NAMESPACES)
        if not party_elem:
            return Party(name="Unknown")
        
        party_elem = party_elem[0]
        name = self._extract_text(party_elem, './/cbc:Name') or "Unknown"
        tax_no = self._extract_text(party_elem, './/cbc:CompanyID')
        
        # 提取地址信息
        address = None
        address_elem = party_elem.xpath('.//cac:PostalAddress', namespaces=self.NAMESPACES)
        if address_elem:
            address = Address(
                street=self._extract_text(address_elem[0], './/cbc:StreetName'),
                city=self._extract_text(address_elem[0], './/cbc:CityName'),
                country=self._extract_text(address_elem[0], './/cbc:Country/cbc:IdentificationCode')
            )
        
        return Party(
            name=name,
            tax_no=tax_no,
            address=address
        )
    
    def _extract_items(self, doc) -> List[InvoiceItem]:
        """提取商品明细"""
        items = []
        invoice_lines = doc.xpath('.//cac:InvoiceLine', namespaces=self.NAMESPACES)
        
        for line in invoice_lines:
            item_id = self._extract_text(line, './/cbc:ID') or str(len(items) + 1)
            description = self._extract_text(line, './/cac:Item/cbc:Name') or "Unknown Item"
            quantity = Decimal(self._extract_text(line, './/cbc:InvoicedQuantity') or '1')
            unit = line.xpath('.//cbc:InvoicedQuantity/@unitCode', namespaces=self.NAMESPACES)
            unit = unit[0] if unit else 'EA'
            amount = self._extract_amount(line, './/cbc:LineExtensionAmount') or Decimal('0')
            unit_price = self._extract_amount(line, './/cac:Price/cbc:PriceAmount') or Decimal('0')
            note = self._extract_text(line, './/cbc:Note')
            
            items.append(InvoiceItem(
                item_id=item_id,
                description=description,
                quantity=quantity,
                unit=unit,
                unit_price=unit_price,
                amount=amount,
                note=note
            ))
        
        return items
    
    def _add_element(self, parent, tag: str, text: str):
        """添加元素"""
        ns, local = tag.split(':')
        elem = etree.SubElement(parent, '{%s}%s' % (self.NAMESPACES[ns], local))
        elem.text = str(text)
        return elem
    
    def _add_amount(self, parent, tag: str, amount: Decimal, currency: str = 'CNY'):
        """添加金额元素"""
        elem = self._add_element(parent, tag, str(amount))
        elem.set('currencyID', currency)
    
    def _add_party(self, parent, tag: str, party: Party):
        """添加参与方信息"""
        ns, local = tag.split(':')
        party_elem = etree.SubElement(parent, '{%s}%s' % (self.NAMESPACES[ns], local))
        party_detail = etree.SubElement(party_elem, '{%s}Party' % self.NAMESPACES['cac'])
        
        self._add_element(party_detail, 'cbc:Name', party.name)
        
        if party.tax_no:
            tax_scheme = etree.SubElement(party_detail, '{%s}PartyTaxScheme' % self.NAMESPACES['cac'])
            self._add_element(tax_scheme, 'cbc:CompanyID', party.tax_no)
        
        if party.address and isinstance(party.address, Address):
            address_elem = etree.SubElement(party_detail, '{%s}PostalAddress' % self.NAMESPACES['cac'])
            if party.address.street:
                self._add_element(address_elem, 'cbc:StreetName', party.address.street)
            if party.address.city:
                self._add_element(address_elem, 'cbc:CityName', party.address.city)
            if party.address.country:
                country_elem = etree.SubElement(address_elem, '{%s}Country' % self.NAMESPACES['cac'])
                self._add_element(country_elem, 'cbc:IdentificationCode', party.address.country)
    
    def _add_invoice_line(self, parent, item: InvoiceItem):
        """添加发票行"""
        line = etree.SubElement(parent, '{%s}InvoiceLine' % self.NAMESPACES['cac'])
        
        self._add_element(line, 'cbc:ID', item.item_id)
        
        quantity_elem = self._add_element(line, 'cbc:InvoicedQuantity', str(item.quantity))
        quantity_elem.set('unitCode', item.unit)
        
        self._add_amount(line, 'cbc:LineExtensionAmount', item.amount)
        
        # 商品信息
        item_elem = etree.SubElement(line, '{%s}Item' % self.NAMESPACES['cac'])
        self._add_element(item_elem, 'cbc:Name', item.description)
        
        # 价格信息
        price_elem = etree.SubElement(line, '{%s}Price' % self.NAMESPACES['cac'])
        self._add_amount(price_elem, 'cbc:PriceAmount', item.unit_price)
        
        # 备注
        if item.note:
            self._add_element(line, 'cbc:Note', item.note)