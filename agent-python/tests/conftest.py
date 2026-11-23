"""pytest 配置：让测试能从 agent-python 目录导入各业务模块。"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
