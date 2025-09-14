package com.toedter.spring.mcpclient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools) {

        this.chatClient = chatClientBuilder
                .defaultSystem("You are a friendly assistant.")
                .defaultToolCallbacks(tools)
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String prompt) {
        System.out.println("Chatting with prompt: " + prompt);
        return this.chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}