"""规则引擎实现 - CEL引擎别名定义"""
from .cel_engine import CELFieldCompletionEngine, CELBusinessValidationEngine

# 为了保持向后兼容性，提供别名
FieldCompletionEngine = CELFieldCompletionEngine
BusinessValidationEngine = CELBusinessValidationEngine