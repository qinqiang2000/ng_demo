#!/usr/bin/env python3
"""
LLM Response Debug Script

Debug what the LLM is actually returning.
"""

import asyncio
from app.services.llm_service import LLMService, RuleGenerationRequest


async def debug_llm_response():
    """Debug LLM response format"""
    print("🔍 Debugging LLM Response Format")
    print("=" * 50)
    
    llm_service = LLMService()
    
    if not llm_service.client:
        print("❌ LLM service not configured")
        return
    
    request = RuleGenerationRequest(
        description="根据供应商名称从数据库查询税号，用于补全缺失的供应商税号字段",
        rule_type="completion",
        context=None,
        examples=[]
    )
    
    # Build the prompt
    prompt = llm_service._build_prompt(request)
    print("📝 Generated Prompt:")
    print("-" * 30)
    print(prompt[:500] + "..." if len(prompt) > 500 else prompt)
    print("-" * 30)
    
    try:
        # Call OpenAI directly
        raw_response = await llm_service._call_openai(prompt)
        print("\n🤖 Raw LLM Response:")
        print("-" * 30)
        print(raw_response)
        print("-" * 30)
        
        # Try to parse
        try:
            parsed = llm_service._parse_response(raw_response, "completion")
            print("\n✅ Parsed Successfully:")
            print(parsed)
        except Exception as parse_error:
            print(f"\n❌ Parse Error: {parse_error}")
            
            # Try basic JSON parsing
            import json
            try:
                json_data = json.loads(raw_response)
                print("\n📊 JSON Structure:")
                for key, value in json_data.items():
                    print(f"  {key}: {type(value)} = {value}")
            except json.JSONDecodeError as json_error:
                print(f"\n❌ JSON Parse Error: {json_error}")
    
    except Exception as e:
        print(f"❌ Error calling LLM: {e}")


if __name__ == "__main__":
    asyncio.run(debug_llm_response())