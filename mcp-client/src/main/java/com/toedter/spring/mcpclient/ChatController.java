package com.toedter.spring.mcpclient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are a friendly assistant that can use tools when needed.")
                .defaultToolCallbacks(tools)
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(required = false) String message) {

        if (message == null) {
            return "Please provide 'message' parameter";
        }

        System.out.println("Chatting with prompt: " + message);
        return this.chatClient
                .prompt()
                .user(message)
//                .options(OpenAiChatOptions.builder()
//                        .parallelToolCalls(false)
//                        .build())
                .call()
                .content();
    }
}