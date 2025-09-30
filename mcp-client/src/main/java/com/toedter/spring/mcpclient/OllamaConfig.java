package com.toedter.spring.mcpclient;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {
    
    @Bean
    public OllamaApi ollamaApi() {
        return new OllamaApi.Builder()
            .baseUrl("http://localhost:11434")
            .build();
    }
}