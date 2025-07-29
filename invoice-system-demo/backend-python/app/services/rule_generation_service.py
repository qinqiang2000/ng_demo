"""Rule Generation Service

This service provides interfaces for LLM-based rule generation with context injection,
rule validation, and example-based generation support.
"""

import re
import uuid
from typing import Dict, List, Optional, Any, Tuple
from pydantic import BaseModel, Field

from ..core.llm_rule_context import RuleType
from ..services.llm_context_service import llm_context_service
from ..services.llm_service import LLMService, RuleGenerationRequest as LLMRequest
from ..utils.logger import get_logger

logger = get_logger(__name__)


class RuleGenerationRequest(BaseModel):
    """Request for rule generation"""
    rule_type: RuleType
    target_field: Optional[str] = None
    field_path: Optional[str] = None  # For validation rules
    description: str = Field(..., description="自然语言描述的规则需求")
    apply_condition: Optional[str] = None
    error_message: Optional[str] = None  # For validation rules
    priority: int = 50
    examples: List[str] = Field(default_factory=list, description="相关示例")


class GeneratedRule(BaseModel):
    """Generated rule result"""
    id: str
    rule_name: str
    rule_type: RuleType
    target_field: Optional[str] = None
    field_path: Optional[str] = None
    rule_expression: str
    apply_to: Optional[str] = None
    error_message: Optional[str] = None
    priority: int = 50
    active: bool = True
    confidence_score: float = Field(ge=0.0, le=1.0, description="规则质量置信度")
    explanation: str = Field(description="规则逻辑解释")


class RuleValidationResult(BaseModel):
    """Rule validation result"""
    is_valid: bool
    errors: List[str] = Field(default_factory=list)
    warnings: List[str] = Field(default_factory=list)
    suggestions: List[str] = Field(default_factory=list)


class RuleGenerationService:
    """Service for LLM-based rule generation"""
    
    def __init__(self):
        self.context_service = llm_context_service
        self.llm_service = LLMService()
        self._rule_patterns = self._load_rule_patterns()
    
    def _load_rule_patterns(self) -> Dict[str, Any]:
        """Load rule patterns for validation and suggestions"""
        return self.context_service.get_pattern_reference()
    
    async def generate_rule_suggestions(
        self, 
        request: RuleGenerationRequest,
        context_requirements: Optional[List[str]] = None
    ) -> List[GeneratedRule]:
        """
        Generate rule suggestions based on natural language description
        
        This method tries to use LLM first, falls back to template-based generation.
        """
        
        try:
            # Try LLM-based generation first
            if self.llm_service.client:
                suggestions = await self._generate_llm_based_rules(request, context_requirements)
                if suggestions:
                    logger.info(f"使用LLM生成了 {len(suggestions)} 个规则建议")
                    return suggestions
        except Exception as e:
            logger.warning(f"LLM规则生成失败，回退到模板方法: {str(e)}")
        
        # Fallback to template-based generation
        context = self.context_service.generate_context(
            rule_type=request.rule_type,
            target_field=request.target_field,
            context_requirements=context_requirements
        )
        
        suggestions = self._generate_template_based_rules(request, context)
        logger.info(f"使用模板方法生成了 {len(suggestions)} 个规则建议")
        return suggestions
    
    async def _generate_llm_based_rules(
        self,
        request: RuleGenerationRequest,
        context_requirements: Optional[List[str]] = None
    ) -> List[GeneratedRule]:
        """Generate rules using LLM"""
        
        # Create LLM request
        llm_request = LLMRequest(
            description=request.description,
            rule_type=request.rule_type.value,
            context=None,  # Context will be generated internally
            examples=request.examples
        )
        
        # Call LLM service
        llm_response = await self.llm_service.generate_rule(llm_request)
        
        if not llm_response.get("success"):
            raise RuntimeError(f"LLM生成失败: {llm_response.get('error', 'Unknown error')}")
        
        # Convert LLM response to GeneratedRule
        rule_data = llm_response["data"]
        
        generated_rule = GeneratedRule(
            id=rule_data.get("id", f"{request.rule_type.value}_{uuid.uuid4().hex[:8]}"),
            rule_name=rule_data["rule_name"],
            rule_type=request.rule_type,
            target_field=rule_data.get("target_field") if request.rule_type == RuleType.COMPLETION else None,
            field_path=rule_data.get("field_path") if request.rule_type == RuleType.VALIDATION else None,
            rule_expression=rule_data["rule_expression"],
            apply_to=rule_data.get("apply_to"),
            error_message=rule_data.get("error_message") if request.rule_type == RuleType.VALIDATION else None,
            priority=rule_data.get("priority", request.priority),
            active=rule_data.get("active", True),
            confidence_score=0.9,  # High confidence for LLM-generated rules
            explanation=f"LLM生成的{request.rule_type.value}规则: {rule_data['rule_name']}"
        )
        
        return [generated_rule]
    
    def _generate_template_based_rules(
        self, 
        request: RuleGenerationRequest,
        context: Any
    ) -> List[GeneratedRule]:
        """Generate template-based rule suggestions (placeholder for LLM integration)"""
        
        suggestions = []
        
        if request.rule_type == RuleType.COMPLETION:
            suggestions.extend(self._generate_completion_suggestions(request))
        else:
            suggestions.extend(self._generate_validation_suggestions(request))
        
        return suggestions
    
    def _generate_completion_suggestions(self, request: RuleGenerationRequest) -> List[GeneratedRule]:
        """Generate completion rule suggestions"""
        suggestions = []
        
        target_field = request.target_field or ""
        description = request.description.lower()
        
        # Database lookup suggestions
        if any(keyword in description for keyword in ['查询', '获取', '从数据库', 'db']):
            if 'tax_no' in target_field or '税号' in description:
                suggestions.append(GeneratedRule(
                    id=f"completion_{uuid.uuid4().hex[:8]}",
                    rule_name=f"从数据库补全{target_field}",
                    rule_type=RuleType.COMPLETION,
                    target_field=target_field,
                    rule_expression="db.companies.tax_number[name=invoice.supplier.name]",
                    apply_to=f"invoice.{target_field} == null",
                    confidence_score=0.9,
                    explanation="从企业信息表中根据名称查询税号"
                ))
            
            elif 'category' in target_field or '分类' in description:
                suggestions.append(GeneratedRule(
                    id=f"completion_{uuid.uuid4().hex[:8]}",
                    rule_name=f"从数据库补全{target_field}",
                    rule_type=RuleType.COMPLETION,
                    target_field=target_field,
                    rule_expression="db.companies.category[name=invoice.supplier.name]",
                    apply_to=f"!has(invoice.{target_field})",
                    confidence_score=0.85,
                    explanation="从企业信息表中根据名称查询分类"
                ))
        
        # Calculation suggestions
        if any(keyword in description for keyword in ['计算', '乘以', '税额', 'tax']):
            if 'tax_amount' in target_field or '税额' in description:
                suggestions.append(GeneratedRule(
                    id=f"completion_{uuid.uuid4().hex[:8]}",
                    rule_name="计算税额",
                    rule_type=RuleType.COMPLETION,
                    target_field=target_field,
                    rule_expression="invoice.total_amount * 0.06",
                    apply_to="invoice.total_amount > 0 && !has(invoice.tax_amount)",
                    confidence_score=0.8,
                    explanation="按6%默认税率计算税额"
                ))
        
        # Default value suggestions
        if any(keyword in description for keyword in ['默认', '设置', '固定值']):
            if 'country' in target_field:
                suggestions.append(GeneratedRule(
                    id=f"completion_{uuid.uuid4().hex[:8]}",
                    rule_name="设置默认国家",
                    rule_type=RuleType.COMPLETION,
                    target_field=target_field,
                    rule_expression="'CN'",
                    apply_to="",
                    confidence_score=0.95,
                    explanation="设置默认国家为中国"
                ))
        
        return suggestions
    
    def _generate_validation_suggestions(self, request: RuleGenerationRequest) -> List[GeneratedRule]:
        """Generate validation rule suggestions"""
        suggestions = []
        
        field_path = request.field_path or ""
        description = request.description.lower()
        
        # Required field validation
        if any(keyword in description for keyword in ['必填', '不能为空', 'required']):
            suggestions.append(GeneratedRule(
                id=f"validation_{uuid.uuid4().hex[:8]}",
                rule_name=f"{field_path}必填校验",
                rule_type=RuleType.VALIDATION,
                field_path=field_path,
                rule_expression=f"has(invoice.{field_path}) && invoice.{field_path} != ''",
                error_message=request.error_message or f"{field_path}不能为空",
                confidence_score=0.95,
                explanation="检查字段是否存在且非空"
            ))
        
        # Format validation
        if any(keyword in description for keyword in ['格式', '正则', 'format', 'pattern']):
            if 'tax_no' in field_path or '税号' in description:
                suggestions.append(GeneratedRule(
                    id=f"validation_{uuid.uuid4().hex[:8]}",
                    rule_name="税号格式校验",
                    rule_type=RuleType.VALIDATION,
                    field_path=field_path,
                    rule_expression=f"invoice.{field_path}.matches('^[0-9]{{15}}[A-Z0-9]{{3}}$')",
                    error_message=request.error_message or "税号格式错误，应为15位数字+3位字母数字组合",
                    confidence_score=0.9,
                    explanation="使用正则表达式校验税号格式"
                ))
            
            elif 'email' in field_path:
                suggestions.append(GeneratedRule(
                    id=f"validation_{uuid.uuid4().hex[:8]}",
                    rule_name="邮箱格式校验",
                    rule_type=RuleType.VALIDATION,
                    field_path=field_path,
                    rule_expression=f"invoice.{field_path}.matches('^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{{2,}}$')",
                    error_message=request.error_message or "邮箱格式错误",
                    confidence_score=0.9,
                    explanation="使用正则表达式校验邮箱格式"
                ))
        
        # Range validation
        if any(keyword in description for keyword in ['范围', '大于', '小于', 'range']):
            if 'amount' in field_path:
                suggestions.append(GeneratedRule(
                    id=f"validation_{uuid.uuid4().hex[:8]}",
                    rule_name=f"{field_path}范围校验",
                    rule_type=RuleType.VALIDATION,
                    field_path=field_path,
                    rule_expression=f"invoice.{field_path} > 0",
                    error_message=request.error_message or f"{field_path}必须大于0",
                    confidence_score=0.85,
                    explanation="校验金额字段必须为正数"
                ))
        
        return suggestions
    
    def validate_rule(self, rule_expression: str, rule_type: RuleType) -> RuleValidationResult:
        """Validate a rule expression for syntax and logic"""
        
        result = RuleValidationResult(is_valid=True)
        
        # Basic syntax validation
        syntax_errors = self._check_syntax(rule_expression)
        result.errors.extend(syntax_errors)
        
        # CEL specific validation
        cel_errors = self._check_cel_syntax(rule_expression)
        result.errors.extend(cel_errors)
        
        # Rule type specific validation
        if rule_type == RuleType.VALIDATION:
            validation_errors = self._check_validation_rule(rule_expression)
            result.errors.extend(validation_errors)
        
        # Smart query validation
        if 'db.' in rule_expression:
            query_errors = self._check_smart_query_syntax(rule_expression)
            result.errors.extend(query_errors)
        
        # Performance warnings
        warnings = self._check_performance_issues(rule_expression)
        result.warnings.extend(warnings)
        
        # Generate suggestions
        suggestions = self._generate_improvement_suggestions(rule_expression)
        result.suggestions.extend(suggestions)
        
        result.is_valid = len(result.errors) == 0
        
        return result
    
    def _check_syntax(self, expression: str) -> List[str]:
        """Check basic syntax errors"""
        errors = []
        
        # Check for unmatched parentheses
        if expression.count('(') != expression.count(')'):
            errors.append("括号不匹配")
        
        # Check for unmatched quotes
        single_quotes = expression.count("'") - expression.count("\\'")
        double_quotes = expression.count('"') - expression.count('\\"')
        
        if single_quotes % 2 != 0:
            errors.append("单引号不匹配")
        if double_quotes % 2 != 0:
            errors.append("双引号不匹配")
        
        # Check for basic SQL injection patterns (in smart queries)
        dangerous_patterns = ['DROP', 'DELETE', 'INSERT', 'UPDATE', '--', ';']
        for pattern in dangerous_patterns:
            if pattern in expression.upper():
                errors.append(f"检测到潜在的危险操作: {pattern}")
        
        return errors
    
    def _check_cel_syntax(self, expression: str) -> List[str]:
        """Check CEL-specific syntax"""
        errors = []
        
        # Check for common CEL function usage
        cel_functions = ['has', 'matches', 'size', 'all', 'map', 'reduce']
        
        # Validate has() usage
        has_pattern = r'has\(([^)]+)\)'
        matches = re.findall(has_pattern, expression)
        for match in matches:
            if not match.startswith('invoice.') and not match.startswith('item.'):
                errors.append(f"has()函数参数应以invoice.或item.开头: {match}")
        
        # Validate matches() usage
        matches_pattern = r'\.matches\(([^)]+)\)'
        matches = re.findall(matches_pattern, expression)
        for match in matches:
            if not (match.startswith("'") and match.endswith("'")) and not (match.startswith('"') and match.endswith('"')):
                errors.append(f"matches()函数需要字符串参数: {match}")
        
        return errors
    
    def _check_validation_rule(self, expression: str) -> List[str]:
        """Check validation rule specific requirements"""
        errors = []
        
        # Validation rules should return boolean
        if not any(op in expression for op in ['==', '!=', '>', '<', '>=', '<=', 'matches', 'has']):
            errors.append("校验规则应包含比较操作符")
        
        return errors
    
    def _check_smart_query_syntax(self, expression: str) -> List[str]:
        """Check smart query syntax"""
        errors = []
        
        # Pattern for smart queries
        query_pattern = r'db\.(\w+)(?:\.(\w+))?\[([^\]]+)\]'
        matches = re.findall(query_pattern, expression)
        
        known_tables = ['companies', 'tax_rates', 'business_rules']
        
        for table, field, conditions in matches:
            if table not in known_tables:
                errors.append(f"未知的数据表: {table}")
            
            # Check condition syntax
            if not conditions.strip():
                errors.append("查询条件不能为空")
        
        return errors
    
    def _check_performance_issues(self, expression: str) -> List[str]:
        """Check for potential performance issues"""
        warnings = []
        
        # Multiple database queries
        db_query_count = expression.count('db.')
        if db_query_count > 2:
            warnings.append(f"表达式包含{db_query_count}个数据库查询，可能影响性能")
        
        # Complex regex patterns
        if '.matches(' in expression:
            warnings.append("正则表达式可能影响性能，建议简化")
        
        return warnings
    
    def _generate_improvement_suggestions(self, expression: str) -> List[str]:
        """Generate suggestions for improving the rule"""
        suggestions = []
        
        # Suggest null checks
        if 'invoice.' in expression and 'has(' not in expression:
            suggestions.append("建议添加has()检查避免null错误")
        
        # Suggest error handling for database queries
        if 'db.' in expression and ' or ' not in expression:
            suggestions.append("建议为数据库查询添加默认值处理")
        
        return suggestions
    
    def get_context_for_field(self, field_name: str, rule_type: RuleType) -> Dict[str, Any]:
        """Get specific context for a field"""
        return self.context_service.generate_minimal_context(rule_type, field_name)
    
    def get_rule_examples(self, pattern_name: str, rule_type: RuleType) -> List[Dict[str, Any]]:
        """Get examples for a specific rule pattern"""
        patterns = self._rule_patterns.get(f"{rule_type}_patterns", {})
        
        if pattern_name in patterns:
            return patterns[pattern_name].get('examples', [])
        
        return []
    
    def analyze_existing_rules(self, rules: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Analyze existing rules for patterns and suggestions"""
        analysis = {
            'total_rules': len(rules),
            'completion_rules': 0,
            'validation_rules': 0,
            'database_queries': 0,
            'common_patterns': {},
            'suggestions': []
        }
        
        for rule in rules:
            rule_type = rule.get('rule_type', 'unknown')
            if rule_type == 'completion':
                analysis['completion_rules'] += 1
            elif rule_type == 'validation':
                analysis['validation_rules'] += 1
            
            rule_expression = rule.get('rule_expression', '')
            if 'db.' in rule_expression:
                analysis['database_queries'] += 1
        
        # Generate suggestions based on analysis
        if analysis['database_queries'] > analysis['total_rules'] * 0.5:
            analysis['suggestions'].append("数据库查询较多，建议考虑缓存策略")
        
        if analysis['validation_rules'] < analysis['completion_rules'] * 0.3:
            analysis['suggestions'].append("建议增加更多校验规则确保数据质量")
        
        return analysis


# Global service instance
rule_generation_service = RuleGenerationService()