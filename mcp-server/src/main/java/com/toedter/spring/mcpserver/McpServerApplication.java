package com.toedter.spring.mcpserver;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {
    @Bean
    public ToolCallbackProvider weatherTools(
            WeatherService weatherService,
            QuoteService quoteService,
            MovieService movieService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherService, quoteService, movieService)
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

}
