"""LLM Context Service

This service generates tailored contexts for LLM-based rule generation.
It combines relevant context pieces efficiently based on rule type and target field.
"""

import yaml
from typing import Dict, List, Optional, Any
from pathlib import Path

from ..core.llm_rule_context import (
    LLMRuleContext, RuleType, RulePattern,
    get_base_context, get_completion_patterns, get_validation_patterns
)
from ..utils.logger import get_logger

logger = get_logger(__name__)

class LLMContextService:
    """Service for generating LLM rule generation contexts"""
    
    def __init__(self):
        self.templates_dir = Path(__file__).parent.parent / "templates" / "rule_generation"
        self._cache = {}
        self._load_templates()
    
    def _load_templates(self):
        """Load context templates from YAML files"""
        try:
            # Load syntax reference
            syntax_file = self.templates_dir / "rule_syntax_reference.yaml"
            if syntax_file.exists():
                with open(syntax_file, 'r', encoding='utf-8') as f:
                    self._cache['syntax'] = yaml.safe_load(f)
            
            # Load domain model reference
            domain_file = self.templates_dir / "domain_model_reference.yaml"
            if domain_file.exists():
                with open(domain_file, 'r', encoding='utf-8') as f:
                    self._cache['domain'] = yaml.safe_load(f)
            
            # Load database schema reference
            db_file = self.templates_dir / "database_schema_reference.yaml"
            if db_file.exists():
                with open(db_file, 'r', encoding='utf-8') as f:
                    self._cache['database'] = yaml.safe_load(f)
            
            # Load rule patterns
            patterns_file = self.templates_dir / "rule_patterns.yaml"
            if patterns_file.exists():
                with open(patterns_file, 'r', encoding='utf-8') as f:
                    self._cache['patterns'] = yaml.safe_load(f)
            
            logger.info(f"加载了 {len(self._cache)} 个模板文件")
            
        except Exception as e:
            logger.error(f"加载模板文件失败: {str(e)}")
            self._cache = {}
    
    def generate_context(
        self, 
        rule_type: RuleType,
        target_field: Optional[str] = None,
        context_requirements: Optional[List[str]] = None
    ) -> LLMRuleContext:
        """
        Generate tailored context for LLM rule generation
        
        Args:
            rule_type: Type of rule to generate (completion/validation)
            target_field: Target field for completion rules
            context_requirements: Specific context components needed
        """
        
        # Start with base context
        context = get_base_context(rule_type)
        context.target_field = target_field
        
        # Add rule patterns based on type
        if rule_type == RuleType.COMPLETION:
            context.rule_patterns = get_completion_patterns()
        else:
            context.rule_patterns = get_validation_patterns()
        
        # Add context-specific hints
        context.hints = self._generate_hints(rule_type, target_field)
        
        # Filter context based on requirements
        if context_requirements:
            context = self._filter_context(context, context_requirements)
        
        # Add field-specific information
        if target_field:
            context = self._enhance_field_context(context, target_field)
        
        logger.debug(f"生成了 {rule_type} 规则的上下文，目标字段: {target_field}")
        return context
    
    def _generate_hints(self, rule_type: RuleType, target_field: Optional[str]) -> List[str]:
        """Generate context-specific hints"""
        hints = []
        
        # General hints based on rule type
        if rule_type == RuleType.COMPLETION:
            hints.extend([
                "补全规则应处理字段为空或null的情况",
                "考虑设置合理的默认值",
                "数据库查询失败时应有fallback值",
                "使用has()函数检查字段存在性"
            ])
        else:  # VALIDATION
            hints.extend([
                "校验规则应返回boolean值",
                "提供清晰的错误消息",
                "考虑使用apply_to条件限制应用范围",
                "处理null和空值的边界情况"
            ])
        
        # Field-specific hints
        if target_field:
            if 'tax' in target_field.lower():
                hints.append("税相关字段注意税率范围和计算精度")
            elif 'amount' in target_field.lower():
                hints.append("金额字段使用Decimal类型，注意精度")
            elif 'email' in target_field.lower():
                hints.append("邮箱字段需要格式校验")
            elif 'tax_no' in target_field.lower():
                hints.append("税号格式校验：15位数字+3位字母数字")
            elif target_field.startswith('items[]:'):
                hints.append("项目级别规则使用item作为上下文变量")
        
        return hints
    
    def _filter_context(self, context: LLMRuleContext, requirements: List[str]) -> LLMRuleContext:
        """Filter context based on specific requirements"""
        # This could be enhanced to remove unnecessary context parts
        # For now, we keep the full context for completeness
        return context
    
    def _enhance_field_context(self, context: LLMRuleContext, target_field: str) -> LLMRuleContext:
        """Add field-specific context information"""
        
        # Find field information
        field_info = None
        for field in context.domain_fields:
            if field.path.endswith(target_field) or field.name == target_field:
                field_info = field
                break
        
        if field_info:
            # Add field-specific patterns
            relevant_patterns = self._get_field_patterns(target_field, context.rule_type)
            context.rule_patterns.extend(relevant_patterns)
        
        return context
    
    def _get_field_patterns(self, target_field: str, rule_type: RuleType) -> List[RulePattern]:
        """Get patterns relevant to specific field"""
        patterns = []
        
        if not self._cache.get('patterns'):
            return patterns
        
        pattern_data = self._cache['patterns']
        pattern_section = f"{rule_type.value}_patterns"
        
        if pattern_section not in pattern_data:
            return patterns
        
        # Find patterns relevant to the field
        for pattern_key, pattern_info in pattern_data[pattern_section].items():
            examples = pattern_info.get('examples', [])
            
            for example in examples:
                if rule_type == RuleType.COMPLETION:
                    if example.get('target_field', '').endswith(target_field):
                        patterns.append(RulePattern(
                            name=pattern_info['name'],
                            type=rule_type,
                            description=example.get('description', pattern_info['description']),
                            template=pattern_info['template'],
                            example=example['rule_expression']
                        ))
                        break
                else:  # VALIDATION
                    if example.get('field_path', '').endswith(target_field):
                        patterns.append(RulePattern(
                            name=pattern_info['name'],
                            type=rule_type,
                            description=example.get('error_message', pattern_info['description']),
                            template=pattern_info['template'],
                            example=example['rule_expression']
                        ))
                        break
        
        return patterns
    
    def get_syntax_reference(self) -> Dict[str, Any]:
        """Get syntax reference for rule generation"""
        return self._cache.get('syntax', {})
    
    def get_domain_reference(self) -> Dict[str, Any]:
        """Get domain model reference"""
        return self._cache.get('domain', {})
    
    def get_database_reference(self) -> Dict[str, Any]:
        """Get database schema reference"""
        return self._cache.get('database', {})
    
    def get_pattern_reference(self) -> Dict[str, Any]:
        """Get rule patterns reference"""
        return self._cache.get('patterns', {})
    
    def generate_minimal_context(
        self, 
        rule_type: RuleType, 
        target_field: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Generate minimal context dictionary for LLM prompts
        Returns a compact representation suitable for token-efficient prompting
        """
        
        logger.info(f"🌐 Context Service: 生成minimal context")
        logger.info(f"📋 Rule Type: {rule_type}")
        logger.info(f"🎯 Target Field: {target_field}")
        
        context = {
            "rule_type": rule_type,
            "target_field": target_field,
        }
        
        # Add essential syntax
        if 'syntax' in self._cache:
            syntax = self._cache['syntax']
            context["syntax"] = {
                "operators": syntax.get('operators', {}),
                "examples": syntax.get('examples', {}).get(rule_type, {})
            }
            logger.info(f"✅ 添加语法上下文: {len(context['syntax']['operators'])} 个操作符")
        
        # Add relevant domain fields
        if target_field and 'domain' in self._cache:
            context["field_info"] = self._get_field_info(target_field)
            logger.info(f"✅ 添加字段信息: {context['field_info'].get('type', 'unknown')} 类型")
        
        # Add database info if needed
        if self._needs_database_context(target_field):
            context["database"] = {
                "tables": self._cache.get('database', {}).get('tables', {}),
                "common_queries": self._cache.get('database', {}).get('common_queries', {})
            }
            logger.info(f"✅ 添加数据库上下文: {len(context['database']['tables'])} 个表")
        
        # Add relevant patterns
        context["patterns"] = self._get_relevant_patterns(rule_type, target_field)
        logger.info(f"✅ 添加相关模式: {len(context['patterns'])} 个")
        
        logger.info(f"🎯 Context生成完成，包含键: {list(context.keys())}")
        return context
    
    def _get_field_info(self, target_field: str) -> Dict[str, Any]:
        """Get information about a specific field"""
        if 'domain' not in self._cache:
            return {}
        
        domain = self._cache['domain']
        
        # Search in invoice structure
        for section_key, section in domain.get('invoice_structure', {}).items():
            if target_field in section:
                return section[target_field]
            
            # Search nested structures
            for field_key, field_info in section.items():
                if isinstance(field_info, dict) and 'path' in field_info:
                    if field_info['path'].endswith(target_field):
                        return field_info
        
        return {}
    
    def _needs_database_context(self, target_field: Optional[str]) -> bool:
        """Determine if database context is needed"""
        if not target_field:
            return True  # Include database context by default
        
        # Fields that typically need database lookups
        db_related_fields = [
            'tax_no', 'tax_number', 'category', 'email', 'address',
            'supplier_category', 'tax_rate', 'dynamic_tax_rate'
        ]
        
        return any(field in target_field.lower() for field in db_related_fields)
    
    def _get_relevant_patterns(self, rule_type: RuleType, target_field: Optional[str]) -> List[Dict[str, Any]]:
        """Get patterns relevant to the rule type and field"""
        if 'patterns' not in self._cache:
            return []
        
        patterns = self._cache['patterns']
        pattern_section = f"{rule_type.value}_patterns"
        
        if pattern_section not in patterns:
            return []
        
        relevant_patterns = []
        
        for pattern_key, pattern_info in patterns[pattern_section].items():
            # Add pattern if it's relevant to the target field or generally useful
            if not target_field or self._is_pattern_relevant(pattern_info, target_field):
                relevant_patterns.append({
                    "name": pattern_info['name'],
                    "description": pattern_info['description'],
                    "template": pattern_info['template'],
                    "examples": pattern_info.get('examples', [])[:2]  # Limit examples
                })
        
        return relevant_patterns[:5]  # Limit to 5 most relevant patterns
    
    def _is_pattern_relevant(self, pattern_info: Dict[str, Any], target_field: str) -> bool:
        """Check if a pattern is relevant to the target field"""
        examples = pattern_info.get('examples', [])
        
        for example in examples:
            # Check if pattern example mentions the target field
            rule_expr = example.get('rule_expression', '')
            target = example.get('target_field', '') or example.get('field_path', '')
            
            if target_field in target or target_field in rule_expr:
                return True
        
        # Some patterns are generally useful
        general_patterns = ['required_field', 'default_value', 'calculation']
        if any(gp in pattern_info.get('name', '').lower() for gp in general_patterns):
            return True
        
        return False


# Global service instance
llm_context_service = LLMContextService()