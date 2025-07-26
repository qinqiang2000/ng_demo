#!/usr/bin/env python3
"""测试API端点的简单脚本"""

import requests
import json

def test_invoice_processing():
    """测试发票处理API"""
    url = "http://localhost:8000/api/invoice/process-file"
    
    # 读取测试文件
    file_path = "/Users/qinqiang02/workspace/ml/ng_demo/data/invoice1.xml"
    
    try:
        with open(file_path, 'rb') as f:
            files = {'file': ('invoice1.xml', f, 'application/xml')}
            
            print("正在测试发票处理API...")
            response = requests.post(url, files=files, timeout=30)
            
            print(f"状态码: {response.status_code}")
            print(f"响应头: {dict(response.headers)}")
            
            if response.status_code == 200:
                try:
                    result = response.json()
                    print("\n=== API响应成功 ===")
                    print(f"处理状态: {result.get('status', 'unknown')}")
                    print(f"消息: {result.get('message', 'no message')}")
                    
                    if 'validation_result' in result:
                        validation = result['validation_result']
                        print(f"\n业务校验结果: {'通过' if validation.get('is_valid') else '失败'}")
                        if validation.get('errors'):
                            print(f"错误信息: {validation['errors']}")
                    
                    print("\n✅ 发票处理API测试成功！")
                    return True
                    
                except json.JSONDecodeError:
                    print(f"响应内容 (非JSON): {response.text[:500]}")
                    return False
            else:
                print(f"❌ API调用失败: {response.text}")
                return False
                
    except FileNotFoundError:
        print(f"❌ 测试文件不存在: {file_path}")
        return False
    except requests.exceptions.RequestException as e:
        print(f"❌ 请求异常: {e}")
        return False
    except Exception as e:
        print(f"❌ 未知错误: {e}")
        return False

if __name__ == "__main__":
    success = test_invoice_processing()
    exit(0 if success else 1)