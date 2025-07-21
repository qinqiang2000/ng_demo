# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a next-generation invoice system MVP demo that demonstrates a modern architecture for configurable invoice processing. The system is built around **KDUBL (Kingdee Universal Business Language)** processing with a rule-based engine that separates business logic from code through YAML configuration files.

## Architecture

The system implements a layered architecture with clear separation of concerns:

- **Business Layer**: KDUBL processing, Domain Objects, configurable rule engine (completion & validation)
- **Channel Layer**: Mock compliance checking and delivery simulation  
- **Configuration Layer**: YAML-based business rules that can be modified without code changes

Key architectural patterns:
- **Domain Object Pattern**: In-memory business models for type-safe processing
- **Configuration-Driven Rules**: Business logic externalized to YAML files
- **Bidirectional Conversion**: KDUBL XML ↔ Domain Object transformation
- **Expression Language**: Simplified CEL-like syntax for rule conditions and actions

## Tech Stack

- **Backend**: Python + FastAPI + Pydantic + lxml + PyYAML
- **Frontend**: React 18 + TypeScript + Ant Design + Axios
- **Rule Engine**: Custom simplified CEL expression evaluator
- **Data Format**: UBL 2.1-based XML (KDUBL)

## Development Commands

### Quick Start
```bash
./start.sh  # One-command startup of both frontend and backend
```

### Backend (Python)
```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python3 -m uvicorn app.main:app --reload --port 8000
```

### Frontend (React)
```bash
cd frontend  
npm install
npm start    # Runs on port 3000 with proxy to backend
npm run build
npm test
```

### Testing
```bash
cd backend
python test_invoice.py  # Run integration tests
```

## Core Concepts

### KDUBL Processing Flow
```
External Data → Business Connector → KDUBL Format → Domain Object → Rule Engine → KDUBL Output → Channel Layer
```

### Domain Objects (`app/models/domain.py`)
- `InvoiceDomainObject`: Core business model with 20+ fields
- `Party`: Supplier/customer information
- `InvoiceItem`: Line item details with calculations
- `Address`: Geographic information

### Rule Engine (`app/core/rule_engine.py`)
- `FieldCompletionEngine`: Auto-fills missing data based on configurable rules
- `BusinessValidationEngine`: Validates business logic with custom error messages
- `SimpleExpressionEvaluator`: CEL-like expression parser supporting field access, comparisons, arithmetic, and logical operations

### Business Rules Configuration (`backend/config/rules.yaml`)
Two types of configurable rules:

**Completion Rules**: Auto-populate missing fields
- `apply_to`: Conditional expression for when to apply
- `target_field`: Domain object field path to populate  
- `rule_expression`: CEL expression to compute the value

**Validation Rules**: Business logic validation
- `apply_to`: Conditional expression for when to apply
- `field_path`: Field to validate
- `rule_expression`: CEL expression returning boolean
- `error_message`: User-friendly error message

### Expression Language Features
- Field path access: `invoice.customer.name`
- Comparisons: `==`, `!=`, `>`, `>=`, `<`, `<=`
- Logical operators: `AND`, `OR`
- Arithmetic: `*`, `-`
- Functions: `has()` for null checking
- String and numeric literals

## Key Files and Components

### Backend Structure
- `app/main.py`: FastAPI application with CORS and endpoints
- `app/services/invoice_service.py`: Core orchestration service
- `app/core/kdubl_converter.py`: XML ↔ Domain Object conversion
- `app/core/rule_engine.py`: Business rule execution engines
- `app/models/`: Pydantic domain models
- `config/rules.yaml`: Business rule configuration

### Frontend Structure  
- `src/components/InvoiceProcessor.tsx`: Main processing interface with file upload, text input, step-by-step processing display, and results visualization
- `src/components/RulesManager.tsx`: Rule configuration display
- `src/services/api.ts`: Centralized API service layer

### Test Data
- `data/invoice1.xml`: Multi-line invoice from 携程广州 to 金蝶广州
- `data/invoice2.xml`: Simple single-item invoice
- `data/invoice3.xml`: Additional test case

## Development Workflows

### Adding New Business Rules
1. Edit `backend/config/rules.yaml`
2. Add completion or validation rules with proper conditions
3. Restart backend service (rules loaded at startup)
4. Test with sample data through frontend interface

### Extending Domain Objects
1. Update Pydantic models in `app/models/domain.py`
2. Update KDUBL converter XPath mappings in `app/core/kdubl_converter.py`
3. Add corresponding rule configurations if needed

### Adding New Connectors
1. Extend base classes in `app/connectors/base.py`
2. Implement connector-specific transformation logic
3. Register connector in service layer

## System Access Points

- Frontend UI: http://localhost:3000
- Backend API: http://localhost:8000  
- Interactive API docs: http://localhost:8000/docs

## Rule Configuration Examples

**Auto-completion rule**:
```yaml
- id: "completion_tax"
  rule_name: "计算税额"
  apply_to: "invoice.total_amount > 0 AND !has(invoice.tax_amount)"
  target_field: "tax_amount"  
  rule_expression: "invoice.total_amount * 0.06"
  priority: 90
  active: true
```

**Business validation rule**:
```yaml
- id: "validation_large_amount"
  rule_name: "大额发票必须有税号"
  apply_to: "invoice.total_amount > 5000"
  field_path: "supplier.tax_number"
  rule_expression: "has(invoice.supplier.tax_number) AND invoice.supplier.tax_number != ''"
  error_message: "金额超过5000元的发票必须提供供应商税号"
  priority: 100
  active: true
```

This architecture enables rapid business rule changes without code deployment and provides a foundation for enterprise-grade invoice processing systems.