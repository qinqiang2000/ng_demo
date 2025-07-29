#!/usr/bin/env python3
"""
初始化演示数据脚本
"""
import asyncio
import sys
import os

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app.database.connection import AsyncSessionLocal
from app.database.crud import CompanyCRUD, TaxRateCRUD, CompanyCreate, TaxRateCreate


async def init_demo_data():
    """初始化演示数据"""
    print("开始初始化演示数据...")
    
    async with AsyncSessionLocal() as db:
        try:
            # 初始化企业数据
            print("正在创建企业数据...")
            companies_data = [
                CompanyCreate(
                    name="金蝶软件（中国）有限公司",
                    tax_number="91440300279156048U",
                    address="深圳市南山区科技中一路软件园2号楼",
                    phone="0755-86213456",
                    email="info@kingdee.com",
                    category="TECH"
                ),
                CompanyCreate(
                    name="携程计算机技术（上海）有限公司",
                    tax_number="913100001332972H87",
                    address="上海市长宁区金钟路968号15楼",
                    phone="021-34064880",
                    email="service@ctrip.com",
                    category="TRAVEL_SERVICE"
                ),
                CompanyCreate(
                    name="携程广州",
                    tax_number="913100001332972H77",
                    address="广州市天河区珠江新城金穗路62号",
                    phone="020-38888888",
                    email="guangzhou@ctrip.com",
                    category="TRAVEL_SERVICE"
                ),
                CompanyCreate(
                    name="广州金蝶软件科技有限公司",
                    tax_number="91440100MA5CYC7K8X",
                    address="广州市天河区体育西路103号维多利广场A塔",
                    phone="020-38680000",
                    email="guangzhou@kingdee.com",
                    category="TECH"
                ),
                CompanyCreate(
                    name="深圳腾讯计算机系统有限公司",
                    tax_number="91440300708461136T",
                    address="深圳市南山区高新区科技中一道腾讯大厦",
                    phone="0755-86013388",
                    email="service@tencent.com",
                    category="TECH"
                ),
                CompanyCreate(
                    name="北京京东世纪贸易有限公司",
                    tax_number="91110000102282799A",
                    address="北京市大兴区京东集团总部",
                    phone="010-89128888",
                    email="service@jd.com",
                    category="TRADING"
                ),
                CompanyCreate(
                    name="上海浦东发展银行股份有限公司",
                    tax_number="91310000132207979N",
                    address="上海市中山东一路12号",
                    phone="021-61618888",
                    email="service@spdb.com.cn",
                    category="GENERAL"
                )
            ]
            
            for company_data in companies_data:
                try:
                    existing = await CompanyCRUD.get_by_tax_number(db, company_data.tax_number)
                    if not existing:
                        await CompanyCRUD.create(db, company_data)
                        print(f"✓ 创建企业: {company_data.name}")
                    else:
                        print(f"- 企业已存在: {company_data.name}")
                except Exception as e:
                    print(f"✗ 创建企业失败: {company_data.name} - {str(e)}")
            
            # 初始化税率配置数据
            print("\n正在创建税率配置数据...")
            tax_rates_data = [
                TaxRateCreate(
                    name="增值税-一般税率",
                    rate=0.13,
                    category="GENERAL",
                    min_amount=0.0,
                    description="一般纳税人标准税率13%"
                ),
                TaxRateCreate(
                    name="增值税-小规模纳税人",
                    rate=0.03,
                    category="GENERAL",
                    min_amount=0.0,
                    max_amount=5000.0,
                    description="小规模纳税人征收率3%，适用于5000元以下"
                ),
                TaxRateCreate(
                    name="增值税-6%税率",
                    rate=0.06,
                    category="GENERAL",
                    min_amount=0.0,
                    description="适用于现代服务业、金融服务等"
                ),
                TaxRateCreate(
                    name="旅游服务-标准税率",
                    rate=0.06,
                    category="TRAVEL_SERVICE",
                    min_amount=0.0,
                    max_amount=5000.0,
                    description="旅游服务标准税率6%，适用于5000元以下"
                ),
                TaxRateCreate(
                    name="旅游服务-高额税率",
                    rate=0.13,
                    category="TRAVEL_SERVICE",
                    min_amount=5000.0,
                    description="旅游服务高额税率13%，适用于5000元及以上"
                ),
                TaxRateCreate(
                    name="科技企业-研发费用",
                    rate=0.06,
                    category="TECH",
                    min_amount=0.0,
                    max_amount=10000.0,
                    description="科技企业研发费用税率6%，适用于10000元以下"
                ),
                TaxRateCreate(
                    name="科技企业-产品销售",
                    rate=0.13,
                    category="TECH",
                    min_amount=10000.0,
                    description="科技企业产品销售税率13%，适用于10000元及以上"
                ),
                TaxRateCreate(
                    name="贸易企业-标准税率",
                    rate=0.13,
                    category="TRADING",
                    min_amount=0.0,
                    description="贸易企业标准增值税税率13%"
                ),
                TaxRateCreate(
                    name="零税率",
                    rate=0.0,
                    category="GENERAL",
                    min_amount=0.0,
                    max_amount=100.0,
                    description="零税率，适用于100元以下小额交易"
                )
            ]
            
            for tax_rate_data in tax_rates_data:
                try:
                    # 检查税率是否已存在（通过名称和分类）
                    existing_rates = await TaxRateCRUD.get_by_category(db, tax_rate_data.category or "GENERAL")
                    exists = any(rate.name == tax_rate_data.name for rate in existing_rates)
                    
                    if not exists:
                        await TaxRateCRUD.create(db, tax_rate_data)
                        print(f"✓ 创建税率配置: {tax_rate_data.name} ({tax_rate_data.rate * 100:.1f}%)")
                    else:
                        print(f"- 税率配置已存在: {tax_rate_data.name}")
                except Exception as e:
                    print(f"✗ 创建税率配置失败: {tax_rate_data.name} - {str(e)}")
            
            print("\n演示数据初始化完成！")
            
            # 统计数据
            companies = await CompanyCRUD.get_all(db, 0, 1000)
            tax_rates = await TaxRateCRUD.get_all(db, 0, 1000)
            
            print(f"\n数据统计:")
            print(f"- 企业总数: {len(companies)}")
            print(f"- 税率配置总数: {len(tax_rates)}")
            
            # 按分类统计企业
            company_categories = {}
            for company in companies:
                category = company.category or "GENERAL"
                company_categories[category] = company_categories.get(category, 0) + 1
            
            print(f"\n企业分类统计:")
            for category, count in company_categories.items():
                category_name = {
                    'GENERAL': '一般企业',
                    'TECH': '科技企业', 
                    'TRAVEL_SERVICE': '旅游服务',
                    'TRADING': '贸易公司'
                }.get(category, category)
                print(f"- {category_name}: {count}个")
            
            # 按分类统计税率
            tax_rate_categories = {}
            for tax_rate in tax_rates:
                category = tax_rate.category or "GENERAL"
                tax_rate_categories[category] = tax_rate_categories.get(category, 0) + 1
            
            print(f"\n税率配置分类统计:")
            for category, count in tax_rate_categories.items():
                category_name = {
                    'GENERAL': '一般税率',
                    'TECH': '科技企业', 
                    'TRAVEL_SERVICE': '旅游服务',
                    'TRADING': '贸易公司'
                }.get(category, category)
                print(f"- {category_name}: {count}个")
            
        except Exception as e:
            print(f"初始化数据失败: {str(e)}")
            import traceback
            traceback.print_exc()


if __name__ == "__main__":
    asyncio.run(init_demo_data())