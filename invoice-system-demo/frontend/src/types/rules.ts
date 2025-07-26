// 规则相关的类型定义

export interface Rule {
  id: string;
  name?: string;  // for backward compatibility
  rule_name?: string;  // actual field from backend
  active: boolean;
  priority: number;
  apply_to: string;
  target_field?: string;
  field_path?: string;
  rule_expression?: string;
  error_message?: string;
}

export interface RuleFormData {
  id?: string;
  rule_name: string;
  apply_to: string;
  target_field?: string;
  field_path?: string;
  rule_expression: string;
  error_message?: string;
  priority: number;
  active: boolean;
}

export interface DomainField {
  type: string;
  description: string;
  fields?: { [key: string]: DomainField };
  item_fields?: { [key: string]: DomainField };
}

export interface FunctionInfo {
  description: string;
  example: string;
  returns: string;
  note?: string;
}