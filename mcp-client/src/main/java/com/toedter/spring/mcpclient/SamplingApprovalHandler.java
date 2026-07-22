package com.toedter.spring.mcpclient;

import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.stereotype.Component;

/**
 * Handles MCP sampling requests ({@code sampling/createMessage}) from the configured mcp-server
 * connection. Per the MCP client guidelines, sampling requests MUST be gated by user approval, and
 * mcp-client — not the server — owns the actual model call: the server never gets direct access to
 * the model or its credentials.
 *
 * <p>The user is shown a binary approve/deny prompt summarizing the request. Editing the outgoing
 * request or the model's response before it is returned to the server (a client SHOULD, per the
 * guidelines) is out of scope for this demo; only approve/deny is supported.
 */
@Component
public class SamplingApprovalHandler {

  private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(5);

  private final ChatClient chatClient;
  private final ApprovalRegistry registry;

  public SamplingApprovalHandler(ChatClient.Builder chatClientBuilder, ApprovalRegistry registry) {
    this.chatClient = chatClientBuilder.build();
    this.registry = registry;
  }

  @McpSampling(clients = "weather-x-mcp-server")
  public CreateMessageResult handleSampling(CreateMessageRequest request) {
    StreamSession session = ToolApprovalContext.current();
    if (session == null) {
      // No interactive channel to ask the user: fail closed rather than
      // silently calling the model on the server's behalf.
      throw new IllegalStateException(
          "Sampling requires an interactive session, which is not available on this endpoint.");
    }

    String id = UUID.randomUUID().toString();
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "sampling");
    event.put("id", id);
    event.put("systemPrompt", request.systemPrompt() == null ? "" : request.systemPrompt());
    event.put("messages", summarize(request));
    session.send(event);

    boolean approved = registry.awaitApproval(id, APPROVAL_TIMEOUT);
    if (!approved) {
      session.send(
          Map.of("type", "status", "text", "\n\n_🚫 Sampling request was not approved._\n\n"));
      throw new IllegalStateException("The user denied the sampling request.");
    }

    session.send(Map.of("type", "status", "text", "\n\n_✅ Sampling approved — generating…_\n\n"));

    String prompt =
        request.messages().stream()
            .map(SamplingApprovalHandler::renderMessage)
            .collect(Collectors.joining("\n"));

    ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      spec = spec.system(request.systemPrompt());
    }
    String responseText = spec.user(prompt).call().content();

    return CreateMessageResult.builder(
            Role.ASSISTANT, responseText == null ? "" : responseText, "mcp-client-llm")
        .build();
  }

  private static String renderMessage(SamplingMessage message) {
    String text =
        message.content() instanceof TextContent textContent
            ? textContent.text()
            : "[non-text content]";
    return message.role() + ": " + text;
  }

  private static List<Map<String, String>> summarize(CreateMessageRequest request) {
    return request.messages().stream()
        .map(
            m ->
                Map.of(
                    "role",
                    m.role().toString(),
                    "text",
                    m.content() instanceof TextContent tc ? tc.text() : "[non-text content]"))
        .toList();
  }
}
