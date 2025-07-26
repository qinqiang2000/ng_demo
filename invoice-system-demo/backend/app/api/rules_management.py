"""规则管理API路由"""
from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Dict, Any, List, Optional
from pydantic import BaseModel
import yaml
from pathlib import Path
import uuid
from datetime import datetime

from ..database.connection import get_db
from ..services.rules_service import RulesManagementService
from ..services.llm_service import LLMService, RuleGenerationRequest
from ..models.rules import FieldCompletionRule, FieldValidationRule

router = APIRouter(prefix="/api/rules", tags=["规则管理"])


class CompletionRuleRequest(BaseModel):
    """补全规则请求模型"""
    rule_name: str
    apply_to: str = ""
    target_field: str
    rule_expression: str
    priority: int = 100
    active: bool = True


class ValidationRuleRequest(BaseModel):
    """校验规则请求模型"""
    rule_name: str
    apply_to: str = ""
    field_path: str
    rule_expression: str
    error_message: str
    priority: int = 100
    active: bool = True


class RuleUpdateRequest(BaseModel):
    """规则更新请求模型"""
    rule_name: Optional[str] = None
    apply_to: Optional[str] = None
    target_field: Optional[str] = None  # 补全规则
    field_path: Optional[str] = None    # 校验规则
    rule_expression: Optional[str] = None
    error_message: Optional[str] = None  # 校验规则
    priority: Optional[int] = None
    active: Optional[bool] = None


class ExpressionValidationRequest(BaseModel):
    """表达式验证请求模型"""
    expression: str
    rule_type: str  # "completion" or "validation"
    context_example: Optional[Dict[str, Any]] = None


@router.get("/")
async def get_all_rules():
    """获取所有规则"""
    try:
        service = RulesManagementService()
        rules = await service.get_all_rules()
        return {
            "success": True,
            "data": rules
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/completion")
async def get_completion_rules():
    """获取所有补全规则"""
    try:
        service = RulesManagementService()
        rules = await service.get_completion_rules()
        return {
            "success": True,
            "data": rules
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/validation")
async def get_validation_rules():
    """获取所有校验规则"""
    try:
        service = RulesManagementService()
        rules = await service.get_validation_rules()
        return {
            "success": True,
            "data": rules
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/completion/{rule_id}")
async def get_completion_rule(rule_id: str):
    """获取指定补全规则"""
    try:
        service = RulesManagementService()
        rule = await service.get_completion_rule(rule_id)
        if not rule:
            raise HTTPException(status_code=404, detail="规则不存在")
        return {
            "success": True,
            "data": rule
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/validation/{rule_id}")
async def get_validation_rule(rule_id: str):
    """获取指定校验规则"""
    try:
        service = RulesManagementService()
        rule = await service.get_validation_rule(rule_id)
        if not rule:
            raise HTTPException(status_code=404, detail="规则不存在")
        return {
            "success": True,
            "data": rule
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/completion")
async def create_completion_rule(rule: CompletionRuleRequest, db: AsyncSession = Depends(get_db)):
    """创建补全规则"""
    try:
        service = RulesManagementService()
        # 生成唯一ID
        rule_id = f"completion_{str(uuid.uuid4())[:8]}"
        
        rule_data = {
            "id": rule_id,
            "rule_name": rule.rule_name,
            "apply_to": rule.apply_to,
            "target_field": rule.target_field,
            "rule_expression": rule.rule_expression,
            "priority": rule.priority,
            "active": rule.active
        }
        
        # 验证表达式
        await service.validate_expression(rule.rule_expression, "completion", db)
        
        # 创建规则
        created_rule = await service.create_completion_rule(rule_data)
        
        return {
            "success": True,
            "data": created_rule,
            "message": "补全规则创建成功"
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/validation")
async def create_validation_rule(rule: ValidationRuleRequest, db: AsyncSession = Depends(get_db)):
    """创建校验规则"""
    try:
        service = RulesManagementService()
        # 生成唯一ID
        rule_id = f"validation_{str(uuid.uuid4())[:8]}"
        
        rule_data = {
            "id": rule_id,
            "rule_name": rule.rule_name,
            "apply_to": rule.apply_to,
            "field_path": rule.field_path,
            "rule_expression": rule.rule_expression,
            "error_message": rule.error_message,
            "priority": rule.priority,
            "active": rule.active
        }
        
        # 验证表达式
        await service.validate_expression(rule.rule_expression, "validation", db)
        
        # 创建规则
        created_rule = await service.create_validation_rule(rule_data)
        
        return {
            "success": True,
            "data": created_rule,
            "message": "校验规则创建成功"
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.put("/completion/{rule_id}")
async def update_completion_rule(rule_id: str, rule: RuleUpdateRequest, db: AsyncSession = Depends(get_db)):
    """更新补全规则"""
    try:
        service = RulesManagementService()
        
        # 如果有表达式更新，先验证
        if rule.rule_expression:
            await service.validate_expression(rule.rule_expression, "completion", db)
        
        # 更新规则
        updated_rule = await service.update_completion_rule(rule_id, rule.model_dump(exclude_unset=True))
        
        if not updated_rule:
            raise HTTPException(status_code=404, detail="规则不存在")
        
        return {
            "success": True,
            "data": updated_rule,
            "message": "补全规则更新成功"
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.put("/validation/{rule_id}")
async def update_validation_rule(rule_id: str, rule: RuleUpdateRequest, db: AsyncSession = Depends(get_db)):
    """更新校验规则"""
    try:
        service = RulesManagementService()
        
        # 如果有表达式更新，先验证
        if rule.rule_expression:
            await service.validate_expression(rule.rule_expression, "validation", db)
        
        # 更新规则
        updated_rule = await service.update_validation_rule(rule_id, rule.model_dump(exclude_unset=True))
        
        if not updated_rule:
            raise HTTPException(status_code=404, detail="规则不存在")
        
        return {
            "success": True,
            "data": updated_rule,
            "message": "校验规则更新成功"
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.delete("/completion/{rule_id}")
async def delete_completion_rule(rule_id: str):
    """删除补全规则"""
    try:
        service = RulesManagementService()
        success = await service.delete_completion_rule(rule_id)
        
        if not success:
            raise HTTPException(status_code=404, detail="规则不存在")
        
        return {
            "success": True,
            "message": "补全规则删除成功"
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/validation/{rule_id}")
async def delete_validation_rule(rule_id: str):
    """删除校验规则"""
    try:
        service = RulesManagementService()
        success = await service.delete_validation_rule(rule_id)
        
        if not success:
            raise HTTPException(status_code=404, detail="规则不存在")
        
        return {
            "success": True,
            "message": "校验规则删除成功"
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/reload")
async def reload_rules():
    """重新加载规则配置"""
    try:
        service = RulesManagementService()
        await service.reload_rules()
        
        return {
            "success": True,
            "message": "规则配置重新加载成功"
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/validate-expression")
async def validate_expression(request: ExpressionValidationRequest, db: AsyncSession = Depends(get_db)):
    """验证表达式语法"""
    try:
        service = RulesManagementService()
        result = await service.validate_expression(
            request.expression, 
            request.rule_type, 
            db,
            request.context_example
        )
        
        return {
            "success": True,
            "data": result,
            "message": "表达式语法验证通过"
        }
    except Exception as e:
        return {
            "success": False,
            "error": str(e),
            "message": "表达式语法验证失败"
        }


@router.get("/domain-fields")
async def get_domain_fields():
    """获取可用的领域对象字段"""
    try:
        service = RulesManagementService()
        fields = await service.get_domain_fields()
        
        return {
            "success": True,
            "data": fields
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/functions")
async def get_available_functions():
    """获取可用的函数列表"""
    try:
        service = RulesManagementService()
        functions = await service.get_available_functions()
        
        return {
            "success": True,
            "data": functions
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/generate-llm")
async def generate_rule_with_llm(request: RuleGenerationRequest, db: AsyncSession = Depends(get_db)):
    """使用LLM生成规则"""
    try:
        llm_service = LLMService()
        result = await llm_service.generate_rule(request)
        
        if not result["success"]:
            raise HTTPException(status_code=400, detail=result["error"])
        
        # 验证生成的规则表达式
        rules_service = RulesManagementService()
        rule_data = result["data"]
        
        if "rule_expression" in rule_data:
            validation_result = await rules_service.validate_expression(
                rule_data["rule_expression"], 
                request.rule_type, 
                db
            )
            
            if not validation_result["valid"]:
                return {
                    "success": False,
                    "error": f"生成的规则表达式语法错误: {validation_result['error']}",
                    "generated_rule": rule_data,
                    "validation_error": validation_result
                }
        
        # 关闭LLM服务连接
        await llm_service.close()
        
        return {
            "success": True,
            "data": rule_data,
            "message": "规则生成成功",
            "prompt_info": result.get("prompt_used", "")
        }
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/llm-status")
async def get_llm_status():
    """获取LLM服务状态"""
    try:
        llm_service = LLMService()
        
        # 检查配置状态
        has_api_key = bool(llm_service.config.api_key)
        has_client = llm_service.client is not None
        
        status = {
            "available": has_api_key and has_client,
            "provider": llm_service.config.provider,
            "model": llm_service.config.model,
            "has_api_key": has_api_key,
            "base_url": llm_service.config.base_url
        }
        
        await llm_service.close()
        
        return {
            "success": True,
            "data": status
        }
        
    except Exception as e:
        return {
            "success": False,
            "error": str(e),
            "data": {
                "available": False,
                "provider": "unknown",
                "model": "unknown",
                "has_api_key": False
            }
        }