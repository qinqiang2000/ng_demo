#!/bin/bash

# 启动脚本

echo "=== 下一代开票系统 MVP Demo 启动脚本 ==="
echo ""

# 检查Python环境
if ! command -v python3 &> /dev/null; then
    echo "错误: 未找到Python3，请先安装Python3"
    exit 1
fi

# 检查Node环境
if ! command -v node &> /dev/null; then
    echo "错误: 未找到Node.js，请先安装Node.js"
    exit 1
fi

# 复制示例数据
echo "1. 准备示例数据..."
if [ ! -d "backend/data" ]; then
    cp -r ../data backend/
    echo "   ✓ 示例数据已复制"
else
    echo "   - 示例数据已存在"
fi

# 安装后端依赖
echo ""
echo "2. 安装后端依赖..."
cd backend

# 创建并激活虚拟环境
echo "   创建或激活Python虚拟环境..."
if [ ! -d ".venv" ]; then
    python3 -m venv .venv
fi
source .venv/bin/activate

pip install -r requirements.txt

# 初始化演示数据
echo ""
echo "3. 初始化演示数据..."
python init_demo_data.py

# 启动后端服务
echo ""
echo "4. 启动后端服务..."
echo "   后端地址: http://localhost:8000"
echo "   API文档: http://localhost:8000/docs"
echo ""
python3 -m uvicorn app.main:app --reload --port 8000 &
BACKEND_PID=$!

# 安装前端依赖
echo ""
echo "5. 安装前端依赖..."
cd ../frontend
if [ ! -d "node_modules" ]; then
    echo "   正在安装前端依赖..."
    npm install
else
    echo "   - 前端依赖已安装，跳过安装步骤"
fi

# 启动前端服务
echo ""
echo "6. 启动前端服务..."
echo "   前端地址: http://localhost:3000"
echo ""
npm start &
FRONTEND_PID=$!

echo ""
echo "=== 系统启动完成 ==="
echo ""
echo "访问地址:"
echo "  - 前端界面: http://localhost:3000"
echo "  - 后端API: http://localhost:8000"
echo "  - API文档: http://localhost:8000/docs"
echo ""
echo "按 Ctrl+C 停止所有服务"

# 等待用户中断
trap "kill $BACKEND_PID $FRONTEND_PID; exit" INT
wait

# 退出虚拟环境
deactivate