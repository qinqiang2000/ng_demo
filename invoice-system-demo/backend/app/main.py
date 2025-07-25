"""FastAPI主应用"""
from fastapi import FastAPI, HTTPException, UploadFile, File, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from typing import Dict, Any, Optional, List
import os

from .services.invoice_service import InvoiceProcessingService
from .services.channel_service import MockChannelService
from .connectors.base import BusinessConnectorRegistry
from .database.connection import init_database, get_db
from .api.data_management import router as data_router

app = FastAPI(
    title="下一代开票系统 MVP Demo",
    description="基于KDUBL和规则引擎的配置化开票系统",
    version="0.1.0"
)

# CORS配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化服务
invoice_service = InvoiceProcessingService()
channel_service = MockChannelService()
connector_registry = BusinessConnectorRegistry()

# 注册路由
app.include_router(data_router)

# 挂载静态文件服务 - 示例数据（统一使用backend/data目录）
data_dir = os.path.join(os.path.dirname(__file__), "..", "data")
if os.path.exists(data_dir):
    app.mount("/data", StaticFiles(directory=data_dir), name="data")


class ProcessInvoicesRequest(BaseModel):
    """统一发票处理请求"""
    kdubl_xml: Optional[str] = None  # 单张发票的XML字符串
    kdubl_list: Optional[List[str]] = None  # 多张发票的XML字符串列表
    source_system: str = "ERP"
    merge_strategy: str = "by_tax_party"
    merge_config: Optional[Dict[str, Any]] = None


class DeliverInvoiceRequest(BaseModel):
    """交付发票请求"""
    invoice_data: Dict[str, Any]
    delivery_channel: str = "email"


@app.get("/")
async def root():
    """根路径"""
    return {
        "message": "下一代开票系统 MVP Demo",
        "version": "0.1.0",
        "endpoints": {
            "process": "/api/invoice/process",
            "process_files": "/api/invoice/process-files",
            "validate": "/api/invoice/validate", 
            "rules": "/api/rules",
            "connectors": "/api/connectors",
            "data": "/api/data"
        }
    }


@app.post("/api/invoice/process")
async def process_invoices_json(request: ProcessInvoicesRequest, db: AsyncSession = Depends(get_db)):
    """处理发票 - JSON格式输入（支持单张和批量）"""
    try:
        # 使用数据库会话创建发票服务
        invoice_service_with_db = InvoiceProcessingService(db)
        
        # 确定输入数据
        if request.kdubl_xml:
            # 单张发票
            inputs = request.kdubl_xml
        elif request.kdubl_list:
            # 批量发票
            inputs = request.kdubl_list
        else:
            raise HTTPException(status_code=400, detail="必须提供 kdubl_xml 或 kdubl_list")
        
        result = await invoice_service_with_db.process_invoices(
            inputs=inputs,
            source_system=request.source_system,
            merge_strategy=request.merge_strategy,
            merge_config=request.merge_config
        )
        return result
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/invoice/process-files")
async def process_invoice_files(
    files: List[UploadFile] = File(...),
    source_system: str = "ERP",
    merge_strategy: str = "by_tax_party",
    merge_config: Optional[str] = None,
    db: AsyncSession = Depends(get_db)
):
    """处理上传的发票文件（支持单张和批量）"""
    try:
        if not files:
            raise HTTPException(status_code=400, detail="至少需要上传一个文件")
        
        if len(files) > 50:  # 限制批量处理文件数量
            raise HTTPException(status_code=400, detail="批量处理文件数量不能超过50个")
        
        # 解析合并配置
        parsed_merge_config = None
        if merge_config:
            try:
                import json
                parsed_merge_config = json.loads(merge_config)
            except:
                raise HTTPException(status_code=400, detail="merge_config 必须是有效的JSON字符串")
        
        # 使用数据库会话创建发票服务
        invoice_service_with_db = InvoiceProcessingService(db)
        
        # 执行统一处理
        result = await invoice_service_with_db.process_invoices(
            inputs=files,
            source_system=source_system,
            merge_strategy=merge_strategy,
            merge_config=parsed_merge_config
        )
        
        return result
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"文件处理失败: {str(e)}")


@app.post("/api/invoice/validate-compliance")
async def validate_compliance(request: ProcessInvoicesRequest):
    """合规性校验（模拟通道层）"""
    try:
        # 取第一个KDUBL进行校验
        kdubl_xml = request.kdubl_xml or (request.kdubl_list[0] if request.kdubl_list else None)
        if not kdubl_xml:
            raise HTTPException(status_code=400, detail="必须提供 kdubl_xml 或 kdubl_list")
            
        result = channel_service.validate_compliance(
            kdubl_xml,
            country="CN"
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/invoice/deliver")
async def deliver_invoice(request: DeliverInvoiceRequest):
    """交付发票（模拟）"""
    try:
        result = channel_service.deliver_invoice(
            request.invoice_data,
            request.delivery_channel
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/rules")
async def get_rules():
    """获取当前加载的规则"""
    try:
        rules = invoice_service.get_loaded_rules()
        return {
            "success": True,
            "data": rules
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/connectors")
async def get_connectors():
    """获取可用的业务连接器"""
    return {
        "success": True,
        "data": connector_registry.list_connectors()
    }


@app.get("/api/sample-data")
async def get_sample_data():
    """获取示例数据（统一使用backend/data目录）"""
    data_dir = os.path.join(os.path.dirname(__file__), "..", "data")
    sample_files = []
    
    if os.path.exists(data_dir):
        for file in os.listdir(data_dir):
            if file.endswith('.xml'):
                sample_files.append({
                    "filename": file,
                    "path": f"/data/{file}"
                })
    
    return {
        "success": True,
        "data": sample_files
    }


@app.on_event("startup")
async def startup_event():
    """应用启动时初始化数据库"""
    await init_database()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)