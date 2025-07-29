#!/bin/bash

# Python后端启动脚本
echo "=== 启动Python后端服务 ==="

# 检查Python环境
if ! command -v python3 &> /dev/null; then
    echo "错误: 未找到Python3，请先安装Python3"
    exit 1
fi

# 进入Python后端目录
cd "$(dirname "$0")/../backend-python"

# 检查并创建虚拟环境
echo "1. 检查Python虚拟环境..."
if [ ! -d ".venv" ]; then
    echo "   创建新的虚拟环境..."
    python3 -m venv .venv
    echo "   ✓ 虚拟环境已创建"
else
    echo "   ✓ 虚拟环境已存在"
fi

# 激活虚拟环境
echo "2. 激活虚拟环境..."
source .venv/bin/activate

# 检查并安装依赖
echo "3. 检查Python依赖..."
if ! python -c "import uvicorn" 2>/dev/null; then
    echo "   安装Python依赖..."
    pip install -r requirements.txt
    echo "   ✓ 依赖安装完成"
else
    echo "   ✓ 依赖已安装"
fi

# 初始化数据
echo "4. 初始化演示数据..."
python init_demo_data.py

# 启动服务
echo ""
echo "5. 启动Python后端服务..."
echo "   后端地址: http://localhost:8000"
echo "   API文档: http://localhost:8000/docs"
echo ""
echo "   使用虚拟环境: $(which python)"
echo ""
python -m uvicorn app.main:app --reload --port 8000