package com.toedter.spring.mcpclient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
/**
 * Exposes the chat endpoints consumed by the Angular chatbot. The streaming
 * endpoint drives a human-in-the-loop flow: whenever the model wants to run an
 * MCP tool, an {@code approval} event is pushed to the browser and the tool
 * call blocks until the user approves or denies it via {@code /api/chat/approve}.
 */
@RestController
public class ChatController {
    private final ChatClient chatClient;
    private final ApprovalRegistry approvalRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ChatController(ChatClient.Builder chatClientBuilder,
                          ToolCallbackProvider tools,
                          ApprovalRegistry approvalRegistry) {
        this.approvalRegistry = approvalRegistry;
        // Wrap every MCP tool so it requires user approval before executing.
        List<ToolCallback> guardedTools = Arrays.stream(tools.getToolCallbacks())
                .map(tc -> (ToolCallback) new ApprovalToolCallback(tc, approvalRegistry))
                .toList();
        this.chatClient = chatClientBuilder
                .defaultSystem("You are a friendly assistant that can use tools when needed.")
                .defaultToolCallbacks(guardedTools.toArray(new ToolCallback[0]))
                .build();
    }
    @GetMapping("/chat")
    public String chat(@RequestParam(required = false) String message) {
        if (message == null || message.isBlank()) {
            return "Please provide 'message' parameter";
        }
        return this.chatClient.prompt().user(message).call().content();
    }
    /** Non-streaming endpoint (kept for compatibility). */
    @PostMapping("/api/chat")
    public Map<String, Object> apiChat(@RequestBody DeepChatRequest request) {
        String message = request.lastUserMessage();
        String answer = this.chatClient.prompt().user(message).call().content();
        return Map.of("text", answer == null ? "" : answer);
    }
    /**
     * Streaming endpoint driven by the Angular deep-chat custom handler.
     * Emits JSON events over SSE: {@code approval}, {@code status}, {@code chunk},
     * {@code error} and then completes. Tool calls pause here until the user
     * approves them via the approve endpoint.
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> apiChatStream(@RequestBody DeepChatRequest request) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        String message = request.lastUserMessage();
        if (message == null || message.isBlank()) {
            sink.tryEmitNext(event(Map.of("type", "chunk", "text", "Please provide a message.")));
            sink.tryEmitComplete();
            return sink.asFlux();
        }
        StreamSession session = new StreamSession(sink, objectMapper);
        Schedulers.boundedElastic().schedule(() -> {
            ToolApprovalContext.set(session);
            try {
                String answer = this.chatClient.prompt().user(message).call().content();
                streamWords(sink, answer == null ? "" : answer);
                sink.tryEmitComplete();
            } catch (Exception e) {
                sink.tryEmitNext(event(Map.of("type", "error", "message",
                        e.getMessage() == null ? "Unexpected error" : e.getMessage())));
                sink.tryEmitComplete();
            } finally {
                ToolApprovalContext.clear();
            }
        });
        return sink.asFlux();
    }
    /** Receives the user's approve/deny decision for a pending tool call. */
    @PostMapping("/api/chat/approve")
    public Map<String, Object> approve(@RequestBody ApprovalDecision decision) {
        approvalRegistry.complete(decision.id(), decision.approved());
        return Map.of("ok", true);
    }
    private void streamWords(Sinks.Many<String> sink, String text) {
        // Split keeping trailing spaces so words re-join naturally in the UI.
        String[] tokens = text.split("(?<= )");
        for (String token : tokens) {
            sink.tryEmitNext(event(Map.of("type", "chunk", "text", token)));
            try {
                Thread.sleep(15);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
    private String event(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"serialization failed\"}";
        }
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
    public record ApprovalDecision(String id, boolean approved) {
    }
}
