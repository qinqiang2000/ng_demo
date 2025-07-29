"""
数据库连接配置
"""
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import declarative_base
import os

# 数据库配置 - 数据库文件移动到shared/database/目录
# 从backend-python目录向上找到项目根目录，然后定位到shared目录
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.join(current_dir, "..", "..", "..")  # 到达 invoice-system-demo
db_path = os.path.join(project_root, "shared", "database", "invoice_system.db")
DATABASE_URL = os.getenv("DATABASE_URL", f"sqlite+aiosqlite:///{db_path}")

# 创建异步引擎
engine = create_async_engine(
    DATABASE_URL,
    echo=True,  # 开发环境显示SQL，生产环境应设为False
    future=True
)

# 创建会话工厂
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False
)

# 创建基类
Base = declarative_base()


async def get_db():
    """获取数据库会话"""
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()


async def init_database():
    """初始化数据库表"""
    async with engine.begin() as conn:
        # 导入所有模型以确保表被创建
        from app.database.models import Company, TaxRate
        await conn.run_sync(Base.metadata.create_all)