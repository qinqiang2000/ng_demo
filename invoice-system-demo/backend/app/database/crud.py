"""
数据库CRUD操作
"""
from typing import List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, or_
from sqlalchemy.orm import selectinload
from app.database.models import Company, TaxRate, BusinessRule
from pydantic import BaseModel
from datetime import datetime


class CompanyCreate(BaseModel):
    name: str
    tax_number: Optional[str] = None
    address: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    category: str = "GENERAL"


class CompanyUpdate(BaseModel):
    name: Optional[str] = None
    tax_number: Optional[str] = None
    address: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    category: Optional[str] = None
    is_active: Optional[bool] = None


class TaxRateCreate(BaseModel):
    name: str
    rate: float
    category: Optional[str] = None
    min_amount: float = 0.0
    max_amount: Optional[float] = None
    description: Optional[str] = None


class TaxRateUpdate(BaseModel):
    name: Optional[str] = None
    rate: Optional[float] = None
    category: Optional[str] = None
    min_amount: Optional[float] = None
    max_amount: Optional[float] = None
    description: Optional[str] = None
    is_active: Optional[bool] = None


class CompanyCRUD:
    """企业信息CRUD操作"""
    
    @staticmethod
    async def create(db: AsyncSession, company: CompanyCreate) -> Company:
        """创建企业"""
        db_company = Company(**company.model_dump())
        db.add(db_company)
        await db.commit()
        await db.refresh(db_company)
        return db_company
    
    @staticmethod
    async def get_by_id(db: AsyncSession, company_id: int) -> Optional[Company]:
        """根据ID获取企业"""
        result = await db.execute(select(Company).where(Company.id == company_id))
        return result.scalar_one_or_none()
    
    @staticmethod
    async def get_by_tax_number(db: AsyncSession, tax_number: str) -> Optional[Company]:
        """根据税号获取企业"""
        result = await db.execute(
            select(Company).where(Company.tax_number == tax_number)
        )
        return result.scalar_one_or_none()
    
    @staticmethod
    async def get_by_name(db: AsyncSession, name: str) -> Optional[Company]:
        """根据名称获取企业"""
        result = await db.execute(
            select(Company).where(Company.name == name)
        )
        return result.scalar_one_or_none()
    
    @staticmethod
    async def get_by_name_pattern(db: AsyncSession, name_pattern: str) -> List[Company]:
        """根据名称模糊匹配获取企业列表"""
        result = await db.execute(
            select(Company).where(
                and_(
                    Company.name.contains(name_pattern),
                    Company.is_active == True
                )
            )
        )
        return result.scalars().all()
    
    @staticmethod
    async def get_by_category(db: AsyncSession, category: str) -> List[Company]:
        """根据分类获取企业列表"""
        result = await db.execute(
            select(Company).where(
                and_(
                    Company.category == category,
                    Company.is_active == True
                )
            )
        )
        return result.scalars().all()
    
    @staticmethod
    async def get_all(db: AsyncSession, skip: int = 0, limit: int = 100) -> List[Company]:
        """获取所有企业"""
        result = await db.execute(
            select(Company).offset(skip).limit(limit)
        )
        return result.scalars().all()
    
    @staticmethod
    async def update(db: AsyncSession, company_id: int, company_update: CompanyUpdate) -> Optional[Company]:
        """更新企业"""
        db_company = await CompanyCRUD.get_by_id(db, company_id)
        if not db_company:
            return None
        
        update_data = company_update.dict(exclude_unset=True)
        for field, value in update_data.items():
            setattr(db_company, field, value)
        
        await db.commit()
        await db.refresh(db_company)
        return db_company
    
    @staticmethod
    async def delete(db: AsyncSession, company_id: int) -> bool:
        """删除企业"""
        db_company = await CompanyCRUD.get_by_id(db, company_id)
        if not db_company:
            return False
        
        await db.delete(db_company)
        await db.commit()
        return True


class TaxRateCRUD:
    """税率配置CRUD操作"""
    
    @staticmethod
    async def create(db: AsyncSession, tax_rate: TaxRateCreate) -> TaxRate:
        """创建税率配置"""
        db_tax_rate = TaxRate(**tax_rate.model_dump())
        db.add(db_tax_rate)
        await db.commit()
        await db.refresh(db_tax_rate)
        return db_tax_rate
    
    @staticmethod
    async def get_by_id(db: AsyncSession, tax_rate_id: int) -> Optional[TaxRate]:
        """根据ID获取税率"""
        result = await db.execute(select(TaxRate).where(TaxRate.id == tax_rate_id))
        return result.scalar_one_or_none()
    
    @staticmethod
    async def get_by_category_and_amount(
        db: AsyncSession, 
        category: str, 
        amount: float
    ) -> Optional[TaxRate]:
        """根据分类和金额获取适用税率"""
        result = await db.execute(
            select(TaxRate).where(
                and_(
                    TaxRate.category == category,
                    TaxRate.is_active == True,
                    TaxRate.min_amount <= amount,
                    or_(
                        TaxRate.max_amount.is_(None),
                        TaxRate.max_amount >= amount
                    )
                )
            ).order_by(TaxRate.min_amount.desc())
        )
        return result.scalars().first()
    
    @staticmethod
    async def get_by_category(db: AsyncSession, category: str) -> List[TaxRate]:
        """根据分类获取税率列表"""
        result = await db.execute(
            select(TaxRate).where(
                and_(
                    TaxRate.category == category,
                    TaxRate.is_active == True
                )
            )
        )
        return result.scalars().all()
    
    @staticmethod
    async def get_all(db: AsyncSession, skip: int = 0, limit: int = 100) -> List[TaxRate]:
        """获取所有税率配置"""
        result = await db.execute(
            select(TaxRate).offset(skip).limit(limit)
        )
        return result.scalars().all()
    
    @staticmethod
    async def update(db: AsyncSession, tax_rate_id: int, tax_rate_update: TaxRateUpdate) -> Optional[TaxRate]:
        """更新税率配置"""
        db_tax_rate = await TaxRateCRUD.get_by_id(db, tax_rate_id)
        if not db_tax_rate:
            return None
        
        update_data = tax_rate_update.dict(exclude_unset=True)
        for field, value in update_data.items():
            setattr(db_tax_rate, field, value)
        
        await db.commit()
        await db.refresh(db_tax_rate)
        return db_tax_rate
    
    @staticmethod
    async def delete(db: AsyncSession, tax_rate_id: int) -> bool:
        """删除税率配置"""
        db_tax_rate = await TaxRateCRUD.get_by_id(db, tax_rate_id)
        if not db_tax_rate:
            return False
        
        await db.delete(db_tax_rate)
        await db.commit()
        return True


class DatabaseQueryHelper:
    """数据库查询助手 - 用于规则引擎"""
    
    @staticmethod
    async def get_company_tax_number_by_name(db: AsyncSession, company_name: str) -> Optional[str]:
        """根据公司名称获取税号"""
        print(f"[DEBUG] 查询企业税号: {company_name}")
        
        # 首先尝试精确匹配
        company = await CompanyCRUD.get_by_name(db, company_name)
        if company:
            print(f"[DEBUG] 精确匹配找到企业: {company.name}, 税号: {company.tax_number}")
            return company.tax_number
        
        # 如果精确匹配失败，尝试模糊匹配
        companies = await CompanyCRUD.get_by_name_pattern(db, company_name)
        if companies:
            print(f"[DEBUG] 模糊匹配找到企业: {companies[0].name}, 税号: {companies[0].tax_number}")
            return companies[0].tax_number
        
        print(f"[DEBUG] 未找到企业: {company_name}")
        return None
    
    @staticmethod
    async def get_tax_rate_by_category_and_amount(
        db: AsyncSession, 
        category: str, 
        amount: float
    ) -> Optional[float]:
        """根据分类和金额获取税率"""
        tax_rate = await TaxRateCRUD.get_by_category_and_amount(db, category, amount)
        if tax_rate:
            return tax_rate.rate
        return None
    
    @staticmethod
    async def get_company_category_by_name(db: AsyncSession, company_name: str) -> Optional[str]:
        """根据公司名称获取分类"""
        companies = await CompanyCRUD.get_by_name_pattern(db, company_name)
        if companies:
            return companies[0].category
        return None