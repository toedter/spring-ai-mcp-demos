package com.toedter.spring.mcpclient;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles MCP elicitation requests ({@code elicitation/create}) from the
 * configured mcp-server connection. Per the MCP client guidelines,
 * elicitation is for gathering ordinary information on demand and MUST NOT
 * be used as a credential channel — any request that looks like it is asking
 * for a password, API key, or similar secret is automatically declined
 * without ever prompting the user.
 * <p>
 * Only {@code form}-mode requests (a flat JSON Schema of top-level
 * properties) are supported, matching what the mcp-chatbot form renderer
 * understands; {@code url}-mode requests are declined.
 */
@Component
public class ElicitationApprovalHandler {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);

    private static final Set<String> CREDENTIAL_MARKERS = Set.of(
            "password", "passwd", "secret", "api key", "apikey", "api_key", "token", "credential");

    private final ApprovalRegistry registry;

    public ElicitationApprovalHandler(ApprovalRegistry registry) {
        this.registry = registry;
    }

    @McpElicitation(clients = "weather-x-mcp-server")
    public ElicitResult handleElicitation(ElicitRequest request) {
        if (!(request instanceof ElicitFormRequest formRequest)) {
            // url-mode elicitation is out of scope for this demo's UI.
            return ElicitResult.builder(ElicitResult.Action.DECLINE).build();
        }

        StreamSession session = ToolApprovalContext.current();
        if (session == null) {
            // No interactive channel to ask the user: fail closed.
            return ElicitResult.builder(ElicitResult.Action.CANCEL).build();
        }

        if (asksForCredentials(formRequest)) {
            session.send(Map.of("type", "status", "text",
                    "\n\n_🚫 The server asked for a credential via elicitation, which is not allowed. "
                            + "Declined automatically._\n\n"));
            return ElicitResult.builder(ElicitResult.Action.DECLINE).build();
        }

        String id = UUID.randomUUID().toString();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "elicitation");
        event.put("id", id);
        event.put("message", formRequest.message());
        event.put("requestedSchema", formRequest.requestedSchema());
        session.send(event);

        @SuppressWarnings("unchecked")
        Map<String, Object> decision = registry.await(id, RESPONSE_TIMEOUT, (Map<String, Object>) null);

        if (decision == null) {
            session.send(Map.of("type", "status", "text", "\n\n_⏱️ Elicitation timed out._\n\n"));
            return ElicitResult.builder(ElicitResult.Action.CANCEL).build();
        }

        String action = String.valueOf(decision.getOrDefault("action", "cancel"));
        return switch (action) {
            case "accept" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) decision.getOrDefault("content", Map.of());
                session.send(Map.of("type", "status", "text", "\n\n_✅ Elicitation submitted._\n\n"));
                yield ElicitResult.builder(ElicitResult.Action.ACCEPT).content(content).build();
            }
            case "decline" -> {
                session.send(Map.of("type", "status", "text", "\n\n_🚫 Elicitation declined._\n\n"));
                yield ElicitResult.builder(ElicitResult.Action.DECLINE).build();
            }
            default -> {
                session.send(Map.of("type", "status", "text", "\n\n_🚫 Elicitation cancelled._\n\n"));
                yield ElicitResult.builder(ElicitResult.Action.CANCEL).build();
            }
        };
    }

    /**
     * Scans the requested schema's property names, titles, and descriptions
     * for words that indicate the server is trying to use elicitation as a
     * credential channel, which the MCP client guidelines forbid.
     */
    @SuppressWarnings("unchecked")
    private static boolean asksForCredentials(ElicitFormRequest request) {
        Object propertiesObj = request.requestedSchema().get("properties");
        if (!(propertiesObj instanceof Map<?, ?> properties)) {
            return false;
        }
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (containsCredentialMarker(String.valueOf(entry.getKey()))) {
                return true;
            }
            if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                Object title = propertySchema.get("title");
                Object description = propertySchema.get("description");
                if (containsCredentialMarker(String.valueOf(title)) || containsCredentialMarker(
                        String.valueOf(description))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsCredentialMarker(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String marker : CREDENTIAL_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
