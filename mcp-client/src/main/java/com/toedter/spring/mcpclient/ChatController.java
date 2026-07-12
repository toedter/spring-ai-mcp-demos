package com.toedter.spring.mcpclient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;


@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are a friendly assistant that can use tools when needed.")
                .defaultTools(tools)
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

    /**
     * Endpoint consumed by the deep-chat web component in the Angular chatbot.
     * deep-chat sends {@code {"messages":[{"role":"user","text":"..."}]}} and
     * expects a {@code {"text":"..."}} response.
     */
    @PostMapping("/api/chat")
    public Map<String, String> apiChat(@RequestBody DeepChatRequest request) {
        String message = request.lastUserMessage();
        if (message == null || message.isBlank()) {
            return Map.of("text", "Please provide a message.");
        }

        System.out.println("Chatting with prompt: " + message);
        String answer = this.chatClient
                .prompt()
                .user(message)
                .call()
                .content();

        return Map.of("text", answer);
    }

    /**
     * Streaming variant used by deep-chat when {@code stream} is enabled.
     * Emits Server-Sent Events, each carrying a {@code {"text":"<chunk>"}}
     * fragment that deep-chat appends to the message as it arrives.
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, String>> apiChatStream(@RequestBody DeepChatRequest request) {
        String message = request.lastUserMessage();
        if (message == null || message.isBlank()) {
            return Flux.just(Map.of("text", "Please provide a message."));
        }

        System.out.println("Streaming chat with prompt: " + message);
        return this.chatClient
                .prompt()
                .user(message)
                .stream()
                .content()
                .map(chunk -> Map.of("text", chunk));
    }

    /** Request payload sent by the deep-chat component. */
    public record DeepChatRequest(List<Message> messages) {
        String lastUserMessage() {
            if (messages == null || messages.isEmpty()) {
                return null;
            }
            return messages.get(messages.size() - 1).text();
        }
    }

    public record Message(String role, String text) {
    }
}