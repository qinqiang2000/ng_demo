"""LLM Configuration Settings

This module contains configuration settings for LLM integration.
"""

import os
from typing import Optional
from pydantic import BaseModel, Field


class LLMConfig(BaseModel):
    """LLM configuration settings"""
    
    # OpenAI settings
    openai_api_key: Optional[str] = Field(
        default_factory=lambda: os.getenv("OPENAI_API_KEY"),
        description="OpenAI API key"
    )
    openai_model: str = Field(
        default=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        description="OpenAI model to use"
    )
    openai_base_url: Optional[str] = Field(
        default=os.getenv("OPENAI_BASE_URL"),
        description="Custom OpenAI API base URL"
    )
    
    # Generation settings
    max_tokens: int = Field(
        default=int(os.getenv("LLM_MAX_TOKENS", "2000")),
        description="Maximum tokens for generation"
    )
    temperature: float = Field(
        default=float(os.getenv("LLM_TEMPERATURE", "0.3")),
        ge=0.0,
        le=2.0,
        description="Temperature for generation"
    )
    
    # Request settings
    timeout: int = Field(
        default=int(os.getenv("LLM_TIMEOUT", "30")),
        description="Request timeout in seconds"
    )
    max_retries: int = Field(
        default=int(os.getenv("LLM_MAX_RETRIES", "3")),
        description="Maximum number of retries"
    )
    
    # Feature flags
    enable_llm: bool = Field(
        default=os.getenv("ENABLE_LLM", "true").lower() == "true",
        description="Whether to enable LLM integration"
    )
    fallback_to_template: bool = Field(
        default=os.getenv("LLM_FALLBACK_TO_TEMPLATE", "true").lower() == "true",
        description="Whether to fallback to template-based generation if LLM fails"
    )
    
    def is_configured(self) -> bool:
        """Check if LLM is properly configured"""
        return bool(self.openai_api_key and self.enable_llm)


# Global configuration instance
llm_config = LLMConfig()