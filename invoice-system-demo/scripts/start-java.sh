#!/bin/bash

# Java后端启动脚本
echo "=== 启动Java后端服务 ==="

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java，请先安装Java 17+"
    exit 1
fi

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到Maven，请先安装Maven"
    exit 1
fi

# 进入Java后端目录
cd "$(dirname "$0")/../backend-java"

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "警告: 推荐使用Java 17或更新版本，当前版本: $JAVA_VERSION"
fi

echo "1. 编译Java项目..."
if [ ! -f "target/invoice-system-java-1.0.0.jar" ] || [ "pom.xml" -nt "target/invoice-system-java-1.0.0.jar" ]; then
    echo "   正在编译项目..."
    mvn clean compile -q
    if [ $? -ne 0 ]; then
        echo "   ✗ 编译失败"
        exit 1
    fi
    echo "   ✓ 编译完成"
else
    echo "   ✓ 项目已编译，跳过编译步骤"
fi

echo ""
echo "2. 检查共享数据库..."
if [ ! -f "../shared/database/invoice_system.db" ]; then
    echo "   ⚠ 共享数据库不存在，将由Python后端创建"
    echo "   请先运行Python后端初始化数据"
else
    echo "   ✓ 共享数据库已存在"
fi

echo ""
echo "3. 启动Java后端服务（支持热重载）..."
echo "   后端地址: http://localhost:8000/api"
echo "   API文档: http://localhost:8000/swagger-ui.html"
echo "   热重载: 启用 (修改代码后2-3秒自动重启)"
echo ""
echo "   使用Java版本: $(java -version 2>&1 | head -n 1)"
echo ""

# 设置JVM参数（优化热重载性能）
export JAVA_OPTS="-Xmx1g -Xms512m -Dspring.profiles.active=default -Dspring.devtools.restart.enabled=true"

# 启动Spring Boot应用（支持热重载）
echo "正在启动服务，支持代码热重载..."
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="$JAVA_OPTS" -Dspring-boot.run.fork=true