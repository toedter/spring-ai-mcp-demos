package com.toedter.spring.mcpclient;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps an MCP {@link ToolCallback} so that, before the tool is actually
 * executed, the chatbot user is asked for permission. The intended tool name
 * and its arguments are streamed to the UI, and execution only proceeds if the
 * user approves.
 */
public class ApprovalToolCallback implements ToolCallback {

    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(5);

    private final ToolCallback delegate;
    private final ApprovalRegistry registry;

    public ApprovalToolCallback(ToolCallback delegate, ApprovalRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        StreamSession session = ToolApprovalContext.current();

        // No interactive stream (e.g. the non-streaming endpoints): run directly.
        if (session == null) {
            return delegate.call(toolInput, toolContext);
        }

        ToolDefinition def = getToolDefinition();
        String id = UUID.randomUUID().toString();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "approval");
        request.put("id", id);
        request.put("tool", def.name());
        request.put("description", def.description() == null ? "" : def.description());
        request.put("arguments", toolInput == null ? "{}" : toolInput);
        session.send(request);

        boolean approved = registry.await(id, APPROVAL_TIMEOUT);

        if (approved) {
            session.send(Map.of("type", "status", "text", "\n\n_✅ Approved — running `" + def.name() + "`…_\n\n"));
            return delegate.call(toolInput, toolContext);
        }

        session.send(Map.of("type", "status", "text", "\n\n_🚫 Tool `" + def.name() + "` was not approved._\n\n"));
        return "The user denied permission to run the tool '" + def.name()
                + "'. Do not call it again; explain that you cannot complete the request without it.";
    }
}

