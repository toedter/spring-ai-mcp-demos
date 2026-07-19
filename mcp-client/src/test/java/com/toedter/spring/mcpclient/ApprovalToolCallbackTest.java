package com.toedter.spring.mcpclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalToolCallbackTest {

    private static final ToolDefinition DEFINITION = DefaultToolDefinition.builder()
            .name("some_tool")
            .description("Some tool")
            .inputSchema("{}")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        ToolApprovalContext.clear();
    }

    @Test
    void failsClosedWhenNoInteractiveSessionIsActive() {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(DEFINITION);

        ApprovalToolCallback callback = new ApprovalToolCallback(delegate, new ApprovalRegistry());

        // No StreamSession set on the thread: simulates the non-streaming endpoints.
        String result = callback.call("{}");

        assertThat(result).contains("requires interactive user approval");
        verify(delegate, never()).call(any(), any());
    }

    @Test
    void runsDelegateAndTruncatesOversizedResultWhenApproved() throws Exception {
        String hugeResult = "x".repeat(150_000);
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(DEFINITION);
        when(delegate.call(any(), any())).thenReturn(hugeResult);

        ApprovalRegistry registry = new ApprovalRegistry();
        LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        sink.asFlux().subscribe(events::add);
        StreamSession session = new StreamSession(sink, objectMapper);

        ApprovalToolCallback callback = new ApprovalToolCallback(delegate, registry);

        // ToolApprovalContext is a ThreadLocal, so it must be set on the same
        // thread that runs the (blocking) tool call, exactly as the real
        // streaming endpoint does via Schedulers.boundedElastic().schedule(...).
        CompletableFuture<String> callResult = CompletableFuture.supplyAsync(() -> {
            ToolApprovalContext.set(session);
            try {
                return callback.call("{}", null);
            } finally {
                ToolApprovalContext.clear();
            }
        });

        String firstEvent = events.poll(5, java.util.concurrent.TimeUnit.SECONDS);
        JsonNode approval = objectMapper.readTree(firstEvent);
        assertThat(approval.get("type").asText()).isEqualTo("approval");

        registry.complete(approval.get("id").asText(), true);

        String result = callResult.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(result).hasSizeLessThan(hugeResult.length());
        assertThat(result).contains("truncated");
    }
}
