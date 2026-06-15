package com.muse.muaiagent.demo.invoke;

/**
 * 仅用于测试
 */
public interface TestApiKey {
    String API_KEY = System.getenv().getOrDefault("DASHSCOPE_API_KEY",
            System.getenv().getOrDefault("DASHSCOPEKEY", ""));
}
