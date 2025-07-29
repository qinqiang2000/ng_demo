#!/usr/bin/env python3
"""
统一的日志配置模块
提供标准化的日志记录功能，包含时间戳、文件名、函数名等信息
"""

import logging
import sys
from pathlib import Path
from typing import Optional


def setup_logger(
    name: str,
    level: int = logging.DEBUG,
    format_string: Optional[str] = None
) -> logging.Logger:
    """
    设置并返回一个配置好的logger
    
    Args:
        name: logger名称，通常使用 __name__
        level: 日志级别
        format_string: 自定义格式字符串
    
    Returns:
        配置好的logger实例
    """
    if format_string is None:
        format_string = (
            '%(asctime)s [%(levelname)s] %(name)s:%(lineno)d - %(message)s'
        )
    
    logger = logging.getLogger(name)
    
    # 避免重复添加handler
    if logger.handlers:
        return logger
    
    logger.setLevel(level)
    
    # 创建控制台处理器
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(level)
    
    # 创建格式化器
    formatter = logging.Formatter(
        format_string,
        datefmt='%H:%M:%S'  # 只显示时分秒，不显示毫秒
    )
    console_handler.setFormatter(formatter)
    
    # 添加处理器到logger
    logger.addHandler(console_handler)
    
    # 防止日志向上传播到根logger
    logger.propagate = False
    
    return logger


def get_logger(name: str) -> logging.Logger:
    """
    获取logger的便捷函数
    
    Args:
        name: logger名称，建议使用 __name__
    
    Returns:
        logger实例
    """
    return setup_logger(name)


# 为常用模块预设logger
cel_logger = get_logger('cel_engine')
rule_logger = get_logger('rule_engine')
db_logger = get_logger('database')
crud_logger = get_logger('crud')