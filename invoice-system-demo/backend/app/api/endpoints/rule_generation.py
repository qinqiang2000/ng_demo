"""Rule Generation API Endpoints

This module provides REST API endpoints for LLM-based rule generation,
including rule suggestion, validation, and context retrieval.
"""

from typing import List, Dict, Any, Optional
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from ...core.llm_rule_context import RuleType
from ...services.rule_generation_service import (
    rule_generation_service,
    RuleGenerationRequest,
    GeneratedRule,
    RuleValidationResult
)
from ...services.llm_context_service import llm_context_service
from ...utils.logger import get_logger

logger = get_logger(__name__)

router = APIRouter(prefix="/rule-generation", tags=["rule-generation"])


class RuleValidationRequest(BaseModel):
    """Request for rule validation"""
    rule_expression: str
    rule_type: RuleType


class ContextRequest(BaseModel):
    """Request for context generation"""
    rule_type: RuleType
    target_field: Optional[str] = None
    requirements: Optional[List[str]] = None


@router.post("/suggest", response_model=List[GeneratedRule])
async def generate_rule_suggestions(request: RuleGenerationRequest):
    """
    Generate rule suggestions based on natural language description
    
    Args:
        request: Rule generation request with description and parameters
        
    Returns:
        List of generated rule suggestions with confidence scores
    """
    try:
        logger.info(f"生成规则建议请求: {request.rule_type}, 字段: {request.target_field}")
        
        suggestions = await rule_generation_service.generate_rule_suggestions(request)
        
        logger.info(f"成功生成 {len(suggestions)} 个规则建议")
        return suggestions
        
    except Exception as e:
        logger.error(f"规则生成失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"规则生成失败: {str(e)}")


@router.post("/validate", response_model=RuleValidationResult)
async def validate_rule(request: RuleValidationRequest):
    """
    Validate a rule expression for syntax and logic correctness
    
    Args:
        request: Rule validation request with expression and type
        
    Returns:
        Validation result with errors, warnings, and suggestions
    """
    try:
        logger.info(f"验证规则表达式: {request.rule_expression[:100]}...")
        
        result = rule_generation_service.validate_rule(
            request.rule_expression, 
            request.rule_type
        )
        
        logger.info(f"规则验证完成, 有效: {result.is_valid}, 错误: {len(result.errors)}")
        return result
        
    except Exception as e:
        logger.error(f"规则验证失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"规则验证失败: {str(e)}")


@router.get("/context/{rule_type}")
async def get_rule_context(
    rule_type: RuleType,
    target_field: Optional[str] = Query(None, description="目标字段名"),
    minimal: bool = Query(False, description="是否返回最小化上下文")
):
    """
    Get context information for rule generation
    
    Args:
        rule_type: Type of rule (completion/validation)
        target_field: Optional target field for context filtering
        minimal: Whether to return minimal context for token efficiency
        
    Returns:
        Context information for LLM rule generation
    """
    try:
        logger.info(f"获取规则上下文: {rule_type}, 字段: {target_field}")
        
        if minimal:
            context = llm_context_service.generate_minimal_context(rule_type, target_field)
        else:
            context = llm_context_service.generate_context(rule_type, target_field)
            # Convert to dict for JSON serialization
            context = context.model_dump()
        
        logger.info(f"成功生成上下文")
        return context
        
    except Exception as e:
        logger.error(f"获取上下文失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取上下文失败: {str(e)}")


@router.get("/context/field/{field_name}")
async def get_field_context(field_name: str, rule_type: RuleType):
    """
    Get specific context for a field
    
    Args:
        field_name: Name of the field
        rule_type: Type of rule
        
    Returns:
        Field-specific context information
    """
    try:
        logger.info(f"获取字段上下文: {field_name}, 规则类型: {rule_type}")
        
        context = rule_generation_service.get_context_for_field(field_name, rule_type)
        
        logger.info(f"成功获取字段 {field_name} 的上下文")
        return context
        
    except Exception as e:
        logger.error(f"获取字段上下文失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取字段上下文失败: {str(e)}")


@router.get("/patterns/{rule_type}")
async def get_rule_patterns(rule_type: RuleType):
    """
    Get available rule patterns for a specific rule type
    
    Args:
        rule_type: Type of rule (completion/validation)
        
    Returns:
        Available rule patterns with examples
    """
    try:
        logger.info(f"获取规则模式: {rule_type}")
        
        patterns = llm_context_service.get_pattern_reference()
        pattern_section = f"{rule_type}_patterns"
        
        if pattern_section in patterns:
            result = patterns[pattern_section]
        else:
            result = {}
        
        logger.info(f"成功获取 {rule_type} 规则模式")
        return result
        
    except Exception as e:
        logger.error(f"获取规则模式失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取规则模式失败: {str(e)}")


@router.get("/examples/{pattern_name}")
async def get_pattern_examples(pattern_name: str, rule_type: RuleType):
    """
    Get examples for a specific rule pattern
    
    Args:
        pattern_name: Name of the rule pattern
        rule_type: Type of rule
        
    Returns:
        Examples for the specified pattern
    """
    try:
        logger.info(f"获取模式示例: {pattern_name}, 类型: {rule_type}")
        
        examples = rule_generation_service.get_rule_examples(pattern_name, rule_type)
        
        logger.info(f"成功获取模式 {pattern_name} 的示例")
        return examples
        
    except Exception as e:
        logger.error(f"获取模式示例失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取模式示例失败: {str(e)}")


@router.get("/syntax")
async def get_syntax_reference():
    """
    Get syntax reference for rule expressions
    
    Returns:
        Syntax reference including operators, functions, and examples
    """
    try:
        logger.info("获取语法参考")
        
        syntax = llm_context_service.get_syntax_reference()
        
        logger.info("成功获取语法参考")
        return syntax
        
    except Exception as e:
        logger.error(f"获取语法参考失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取语法参考失败: {str(e)}")


@router.get("/domain")
async def get_domain_reference():
    """
    Get domain model reference for field paths and types
    
    Returns:
        Domain model structure and field information
    """
    try:
        logger.info("获取领域模型参考")
        
        domain = llm_context_service.get_domain_reference()
        
        logger.info("成功获取领域模型参考")
        return domain
        
    except Exception as e:
        logger.error(f"获取领域模型参考失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取领域模型参考失败: {str(e)}")


@router.get("/database")
async def get_database_reference():
    """
    Get database schema reference for smart queries
    
    Returns:
        Database tables, fields, and query patterns
    """
    try:
        logger.info("获取数据库模式参考")
        
        database = llm_context_service.get_database_reference()
        
        logger.info("成功获取数据库模式参考")
        return database
        
    except Exception as e:
        logger.error(f"获取数据库模式参考失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取数据库模式参考失败: {str(e)}")


@router.post("/analyze")
async def analyze_existing_rules(rules: List[Dict[str, Any]]):
    """
    Analyze existing rules for patterns and improvement suggestions
    
    Args:
        rules: List of existing rules to analyze
        
    Returns:
        Analysis results with statistics and suggestions
    """
    try:
        logger.info(f"分析现有规则，共 {len(rules)} 条")
        
        analysis = rule_generation_service.analyze_existing_rules(rules)
        
        logger.info("规则分析完成")
        return analysis
        
    except Exception as e:
        logger.error(f"规则分析失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"规则分析失败: {str(e)}")


# Health check endpoint
@router.get("/health")
async def health_check():
    """Health check for rule generation service"""
    return {
        "status": "healthy",
        "service": "rule-generation",
        "version": "1.0.0"
    }