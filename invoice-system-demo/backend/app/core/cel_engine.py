"""Google CEL (Common Expression Language) 引擎实现"""
import celpy
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple
from datetime import datetime
from ..models.domain import InvoiceDomainObject


class CELExpressionEvaluator:
    """基于Google CEL的表达式求值器"""
    
    def __init__(self):
        self.env = celpy.Environment()
        self._setup_custom_functions()
    
    def _setup_custom_functions(self):
        """设置自定义函数"""
        # CEL环境支持标准函数，这里可以扩展自定义函数
        # 注意：cel-python支持标准CEL内置函数如has()、contains()、matches()等
        
        # 为发票业务添加一些自定义函数示例
        # CEL-Python中添加自定义函数需要通过env配置
        pass
    
    def evaluate(self, expression: str, context: Dict[str, Any]) -> Any:
        """使用CEL计算表达式"""
        try:
            # 编译CEL表达式
            ast = self.env.compile(expression)
            program = self.env.program(ast)
            
            # 准备CEL上下文
            cel_context = self._prepare_cel_context(context)
            
            # 执行表达式
            result = program.evaluate(cel_context)
            
            # 转换结果类型
            return self._convert_result(result)
            
        except Exception as e:
            print(f"CEL表达式执行错误: {expression} - {str(e)}")
            raise e
    
    def _prepare_cel_context(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """准备CEL执行上下文"""
        cel_context = {}
        
        for key, value in context.items():
            if isinstance(value, InvoiceDomainObject):
                # 将Domain Object转换为CEL可识别的格式
                cel_context[key] = self._domain_object_to_cel(value)
            else:
                cel_context[key] = celpy.json_to_cel(value)
        
        return cel_context
    
    def _domain_object_to_cel(self, domain_obj: InvoiceDomainObject) -> Any:
        """将Domain Object转换为CEL对象"""
        # 将Pydantic模型转换为字典，然后转为CEL对象
        domain_dict = self._pydantic_to_dict(domain_obj)
        return celpy.json_to_cel(domain_dict)
    
    def _pydantic_to_dict(self, obj: Any) -> Dict[str, Any]:
        """将Pydantic对象转换为字典"""
        if hasattr(obj, 'model_dump'):
            # Pydantic v2
            data = obj.model_dump()
        elif hasattr(obj, 'dict'):
            # Pydantic v1
            data = obj.dict()
        else:
            # 普通对象
            data = obj if isinstance(obj, dict) else vars(obj)
        
        # 转换日期对象为字符串，CEL-Python不支持date对象
        return self._convert_dates_to_strings(data)
    
    def _convert_dates_to_strings(self, data: Any) -> Any:
        """递归转换特殊对象为CEL兼容类型"""
        if isinstance(data, dict):
            return {k: self._convert_dates_to_strings(v) for k, v in data.items()}
        elif isinstance(data, list):
            return [self._convert_dates_to_strings(item) for item in data]
        elif hasattr(data, 'isoformat'):
            # datetime.date, datetime.datetime 对象
            return data.isoformat()
        elif isinstance(data, Decimal):
            # Decimal转为float，CEL支持float
            return float(data)
        else:
            return data
    
    def _convert_result(self, result: Any) -> Any:
        """转换CEL结果为Python类型"""
        if hasattr(result, 'value'):
            # CEL值对象，提取实际值
            return result.value
        elif isinstance(result, (int, float, str, bool)):
            return result
        elif isinstance(result, dict):
            return result
        else:
            return result
    
    def _set_field_value(self, obj: Any, field_path: str, value: Any):
        """设置字段值"""
        parts = field_path.split('.')
        current = obj
        
        # 导航到父对象
        for part in parts[:-1]:
            if hasattr(current, part):
                current = getattr(current, part)
            else:
                return False
        
        # 设置最后一个字段
        if hasattr(current, parts[-1]):
            # 类型转换
            if isinstance(value, (int, float)) and hasattr(current, parts[-1]):
                # 检查目标字段类型
                current_value = getattr(current, parts[-1])
                if isinstance(current_value, Decimal) or parts[-1] in ['total_amount', 'tax_amount', 'net_amount']:
                    value = Decimal(str(value))
            
            setattr(current, parts[-1], value)
            return True
        elif isinstance(current, dict):
            current[parts[-1]] = value
            return True
        
        return False


class CELFieldCompletionEngine:
    """基于CEL的字段补全引擎"""
    
    def __init__(self):
        self.evaluator = CELExpressionEvaluator()
        self.rules: List = []
        self.execution_log: List[Dict[str, Any]] = []
    
    def load_rules(self, rules: List):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def complete(self, domain: InvoiceDomainObject) -> InvoiceDomainObject:
        """执行字段补全"""
        self.execution_log = []  # 重置日志
        context = {'invoice': domain}
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = self.evaluator.evaluate(rule.apply_to, context)
                    if not should_apply:
                        continue
                
                # 执行规则表达式
                field_value = self.evaluator.evaluate(rule.rule_expression, context)
                
                # 设置字段值
                if field_value is not None:
                    success = self.evaluator._set_field_value(domain, rule.target_field, field_value)
                    if success:
                        log_entry = {
                            "type": "completion",
                            "status": "success",
                            "rule_name": rule.rule_name,
                            "target_field": rule.target_field,
                            "value": field_value,
                            "message": f"CEL字段补全成功: {rule.rule_name} - {rule.target_field} = {field_value}"
                        }
                        self.execution_log.append(log_entry)
                        print(log_entry["message"])
                    else:
                        log_entry = {
                            "type": "completion",
                            "status": "failed",
                            "rule_name": rule.rule_name,
                            "target_field": rule.target_field,
                            "message": f"CEL字段补全失败: {rule.rule_name} - 无法设置字段 {rule.target_field}"
                        }
                        self.execution_log.append(log_entry)
                        print(log_entry["message"])
                        
            except Exception as e:
                log_entry = {
                    "type": "completion",
                    "status": "error",
                    "rule_name": rule.rule_name,
                    "error": str(e),
                    "message": f"CEL字段补全错误: {rule.rule_name} - {str(e)}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
        
        return domain


class CELBusinessValidationEngine:
    """基于CEL的业务校验引擎"""
    
    def __init__(self):
        self.evaluator = CELExpressionEvaluator()
        self.rules: List = []
        self.execution_log: List[Dict[str, Any]] = []
    
    def load_rules(self, rules: List):
        """加载规则"""
        self.rules = sorted(rules, key=lambda r: r.priority, reverse=True)
    
    def validate(self, domain: InvoiceDomainObject) -> Tuple[bool, List[str]]:
        """执行业务校验"""
        self.execution_log = []  # 重置日志
        context = {'invoice': domain}
        errors = []
        
        for rule in self.rules:
            if not rule.active:
                continue
            
            try:
                # 检查应用条件
                if rule.apply_to:
                    should_apply = self.evaluator.evaluate(rule.apply_to, context)
                    if not should_apply:
                        continue
                
                # 执行校验规则
                is_valid = self.evaluator.evaluate(rule.rule_expression, context)
                
                if not is_valid:
                    errors.append(f"{rule.rule_name}: {rule.error_message}")
                    log_entry = {
                        "type": "validation",
                        "status": "failed",
                        "rule_name": rule.rule_name,
                        "error_message": rule.error_message,
                        "message": f"CEL校验失败: {rule.rule_name} - {rule.error_message}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
                else:
                    log_entry = {
                        "type": "validation",
                        "status": "passed",
                        "rule_name": rule.rule_name,
                        "message": f"CEL校验通过: {rule.rule_name}"
                    }
                    self.execution_log.append(log_entry)
                    print(log_entry["message"])
                    
            except Exception as e:
                error_msg = f"{rule.rule_name}: CEL规则执行错误 - {str(e)}"
                errors.append(error_msg)
                log_entry = {
                    "type": "validation",
                    "status": "error",
                    "rule_name": rule.rule_name,
                    "error": str(e),
                    "message": f"CEL校验错误: {rule.rule_name} - {str(e)}"
                }
                self.execution_log.append(log_entry)
                print(log_entry["message"])
        
        return len(errors) == 0, errors