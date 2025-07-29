"""
数据管理API接口
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional
from ..database.connection import get_db
from ..database.crud import CompanyCRUD, TaxRateCRUD, CompanyCreate, CompanyUpdate, TaxRateCreate, TaxRateUpdate
from ..database.models import Company, TaxRate
from pydantic import BaseModel


router = APIRouter(prefix="/api/data", tags=["数据管理"])


class CompanyResponse(BaseModel):
    """企业信息响应模型"""
    id: int
    name: str
    tax_number: Optional[str]
    address: Optional[str]
    phone: Optional[str]
    email: Optional[str]
    category: str
    is_active: bool
    
    class Config:
        from_attributes = True


class TaxRateResponse(BaseModel):
    """税率配置响应模型"""
    id: int
    name: str
    rate: float
    category: Optional[str]
    min_amount: float
    max_amount: Optional[float]
    description: Optional[str]
    is_active: bool
    
    class Config:
        from_attributes = True


# ============ 企业管理接口 ============

@router.post("/companies", response_model=CompanyResponse)
async def create_company(
    company: CompanyCreate,
    db: AsyncSession = Depends(get_db)
):
    """创建企业"""
    try:
        db_company = await CompanyCRUD.create(db, company)
        return CompanyResponse.from_orm(db_company)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"创建企业失败: {str(e)}")


@router.get("/companies", response_model=List[CompanyResponse])
async def get_companies(
    skip: int = Query(0, description="跳过记录数"),
    limit: int = Query(100, description="返回记录数"),
    category: Optional[str] = Query(None, description="企业分类筛选"),
    name_pattern: Optional[str] = Query(None, description="名称模糊匹配"),
    db: AsyncSession = Depends(get_db)
):
    """获取企业列表"""
    try:
        if category:
            companies = await CompanyCRUD.get_by_category(db, category)
        elif name_pattern:
            companies = await CompanyCRUD.get_by_name_pattern(db, name_pattern)
        else:
            companies = await CompanyCRUD.get_all(db, skip, limit)
        
        return [CompanyResponse.from_orm(company) for company in companies]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取企业列表失败: {str(e)}")


@router.get("/companies/{company_id}", response_model=CompanyResponse)
async def get_company(
    company_id: int,
    db: AsyncSession = Depends(get_db)
):
    """根据ID获取企业"""
    company = await CompanyCRUD.get_by_id(db, company_id)
    if not company:
        raise HTTPException(status_code=404, detail="企业不存在")
    return CompanyResponse.from_orm(company)


@router.get("/companies/by-tax-number/{tax_number}", response_model=CompanyResponse)
async def get_company_by_tax_number(
    tax_number: str,
    db: AsyncSession = Depends(get_db)
):
    """根据税号获取企业"""
    company = await CompanyCRUD.get_by_tax_number(db, tax_number)
    if not company:
        raise HTTPException(status_code=404, detail="企业不存在")
    return CompanyResponse.from_orm(company)


@router.put("/companies/{company_id}", response_model=CompanyResponse)
async def update_company(
    company_id: int,
    company_update: CompanyUpdate,
    db: AsyncSession = Depends(get_db)
):
    """更新企业"""
    company = await CompanyCRUD.update(db, company_id, company_update)
    if not company:
        raise HTTPException(status_code=404, detail="企业不存在")
    return CompanyResponse.from_orm(company)


@router.delete("/companies/{company_id}")
async def delete_company(
    company_id: int,
    db: AsyncSession = Depends(get_db)
):
    """删除企业"""
    success = await CompanyCRUD.delete(db, company_id)
    if not success:
        raise HTTPException(status_code=404, detail="企业不存在")
    return {"message": "企业删除成功"}


# ============ 税率管理接口 ============

@router.post("/tax-rates", response_model=TaxRateResponse)
async def create_tax_rate(
    tax_rate: TaxRateCreate,
    db: AsyncSession = Depends(get_db)
):
    """创建税率配置"""
    try:
        db_tax_rate = await TaxRateCRUD.create(db, tax_rate)
        return TaxRateResponse.from_orm(db_tax_rate)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"创建税率配置失败: {str(e)}")


@router.get("/tax-rates", response_model=List[TaxRateResponse])
async def get_tax_rates(
    skip: int = Query(0, description="跳过记录数"),
    limit: int = Query(100, description="返回记录数"),
    category: Optional[str] = Query(None, description="税率分类筛选"),
    db: AsyncSession = Depends(get_db)
):
    """获取税率配置列表"""
    try:
        if category:
            tax_rates = await TaxRateCRUD.get_by_category(db, category)
        else:
            tax_rates = await TaxRateCRUD.get_all(db, skip, limit)
        
        return [TaxRateResponse.from_orm(tax_rate) for tax_rate in tax_rates]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取税率配置列表失败: {str(e)}")


@router.get("/tax-rates/{tax_rate_id}", response_model=TaxRateResponse)
async def get_tax_rate(
    tax_rate_id: int,
    db: AsyncSession = Depends(get_db)
):
    """根据ID获取税率配置"""
    tax_rate = await TaxRateCRUD.get_by_id(db, tax_rate_id)
    if not tax_rate:
        raise HTTPException(status_code=404, detail="税率配置不存在")
    return TaxRateResponse.from_orm(tax_rate)


@router.get("/tax-rates/by-category-amount", response_model=TaxRateResponse)
async def get_tax_rate_by_category_amount(
    category: str = Query(..., description="税率分类"),
    amount: float = Query(..., description="金额"),
    db: AsyncSession = Depends(get_db)
):
    """根据分类和金额获取适用税率"""
    tax_rate = await TaxRateCRUD.get_by_category_and_amount(db, category, amount)
    if not tax_rate:
        raise HTTPException(status_code=404, detail="未找到适用的税率配置")
    return TaxRateResponse.from_orm(tax_rate)


@router.put("/tax-rates/{tax_rate_id}", response_model=TaxRateResponse)
async def update_tax_rate(
    tax_rate_id: int,
    tax_rate_update: TaxRateUpdate,
    db: AsyncSession = Depends(get_db)
):
    """更新税率配置"""
    tax_rate = await TaxRateCRUD.update(db, tax_rate_id, tax_rate_update)
    if not tax_rate:
        raise HTTPException(status_code=404, detail="税率配置不存在")
    return TaxRateResponse.from_orm(tax_rate)


@router.delete("/tax-rates/{tax_rate_id}")
async def delete_tax_rate(
    tax_rate_id: int,
    db: AsyncSession = Depends(get_db)
):
    """删除税率配置"""
    success = await TaxRateCRUD.delete(db, tax_rate_id)
    if not success:
        raise HTTPException(status_code=404, detail="税率配置不存在")
    return {"message": "税率配置删除成功"}


# ============ 数据统计接口 ============

@router.get("/stats")
async def get_data_stats(db: AsyncSession = Depends(get_db)):
    """获取数据统计信息"""
    try:
        companies = await CompanyCRUD.get_all(db, 0, 1000)
        tax_rates = await TaxRateCRUD.get_all(db, 0, 1000)
        
        # 统计企业分类
        company_categories = {}
        for company in companies:
            category = company.category or "GENERAL"
            company_categories[category] = company_categories.get(category, 0) + 1
        
        # 统计税率分类
        tax_rate_categories = {}
        for tax_rate in tax_rates:
            category = tax_rate.category or "GENERAL"
            tax_rate_categories[category] = tax_rate_categories.get(category, 0) + 1
        
        return {
            "companies": {
                "total": len(companies),
                "active": len([c for c in companies if c.is_active]),
                "categories": company_categories
            },
            "tax_rates": {
                "total": len(tax_rates),
                "active": len([t for t in tax_rates if t.is_active]),
                "categories": tax_rate_categories
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取统计信息失败: {str(e)}")