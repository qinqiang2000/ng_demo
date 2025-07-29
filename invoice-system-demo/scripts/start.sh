#!/bin/bash

# 统一启动脚本 - 支持选择后端类型
# 使用方法：
#   ./scripts/start.sh python    # 启动Python后端
#   ./scripts/start.sh java      # 启动Java后端（尚未实现）
#   ./scripts/start.sh frontend  # 启动前端
#   ./scripts/start.sh           # 启动所有服务（默认Python后端）
#   ./scripts/start.sh b         # 启动后端（兼容原有命令，默认Python）
#   ./scripts/start.sh f         # 启动前端（兼容原有命令）

echo "=== 下一代开票系统 MVP Demo 启动脚本 ==="
echo ""

# 获取脚本目录和项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 启动Python后端函数
start_python_backend() {
    echo "=== 启动Python后端服务 ==="
    "$SCRIPT_DIR/start-python.sh"
}

# 启动Java后端函数
start_java_backend() {
    echo "=== 启动Java后端服务 ==="
    "$SCRIPT_DIR/start-java.sh"
}

# 启动前端函数
start_frontend() {
    echo "=== 启动前端服务 ==="
    
    # 检查Node环境
    if ! command -v node &> /dev/null; then
        echo "错误: 未找到Node.js，请先安装Node.js"
        exit 1
    fi

    # 进入前端目录
    cd "$PROJECT_ROOT/frontend"

    # 安装前端依赖
    echo "1. 安装前端依赖..."
    if [ ! -d "node_modules" ]; then
        echo "   正在安装前端依赖..."
        npm install
    else
        echo "   - 前端依赖已安装，跳过安装步骤"
    fi

    # 启动前端服务
    echo ""
    echo "2. 启动前端服务..."
    echo "   前端地址: http://localhost:3000"
    echo ""
    npm start
}

# 解析命令行参数
case "$1" in
    "python")
        start_python_backend
        ;;
    "java")
        start_java_backend
        ;;
    "frontend"|"f")
        start_frontend
        ;;
    "backend"|"b")
        # 兼容原有命令，默认启动Python后端
        start_python_backend
        ;;
    ""|"all")
        # 默认启动所有服务（Python后端 + 前端）
        echo "默认启动Python后端和前端服务..."
        echo ""
        
        # 检查环境
        if ! command -v python3 &> /dev/null; then
            echo "错误: 未找到Python3，请先安装Python3"
            exit 1
        fi
        
        if ! command -v node &> /dev/null; then
            echo "错误: 未找到Node.js，请先安装Node.js"
            exit 1
        fi

        # 启动Python后端（后台运行）
        echo "1. 启动Python后端服务..."
        cd "$PROJECT_ROOT/backend-python"
        
        # 检查并创建虚拟环境
        if [ ! -d ".venv" ]; then
            echo "   创建虚拟环境..."
            python3 -m venv .venv
        fi
        
        # 激活虚拟环境
        source .venv/bin/activate
        
        # 检查并安装依赖
        if ! python -c "import uvicorn" 2>/dev/null; then
            echo "   安装Python依赖..."
            pip install -r requirements.txt
        fi
        
        # 初始化数据
        python init_demo_data.py
        
        # 启动后端服务（后台）
        python -m uvicorn app.main:app --reload --port 8000 &
        BACKEND_PID=$!
        
        echo "   ✓ Python后端已启动 (PID: $BACKEND_PID)"
        echo "   后端地址: http://localhost:8000"
        echo "   API文档: http://localhost:8000/docs"
        
        # 启动前端服务
        echo ""
        echo "2. 启动前端服务..."
        cd "$PROJECT_ROOT/frontend"
        
        if [ ! -d "node_modules" ]; then
            npm install
        fi
        
        npm start &
        FRONTEND_PID=$!
        
        echo "   ✓ 前端已启动 (PID: $FRONTEND_PID)"
        echo "   前端地址: http://localhost:3000"
        
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
        trap "echo ''; echo '正在停止服务...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT
        wait
        ;;
    *)
        echo "使用方法:"
        echo "  ./scripts/start.sh python    # 启动Python后端"
        echo "  ./scripts/start.sh java      # 启动Java后端（尚未实现）"
        echo "  ./scripts/start.sh frontend  # 启动前端"
        echo "  ./scripts/start.sh           # 启动所有服务（默认Python后端）"
        echo ""
        echo "兼容原有命令:"
        echo "  ./scripts/start.sh b         # 启动后端（默认Python）"
        echo "  ./scripts/start.sh f         # 启动前端"
        exit 1
        ;;
esac