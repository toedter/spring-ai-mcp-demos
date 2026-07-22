package com.toedter.spring.mcpclient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Wraps an MCP {@link ToolCallback} so that, before the tool is actually executed, the chatbot user
 * is asked for permission. The intended tool name and its arguments are streamed to the UI, and
 * execution only proceeds if the user approves.
 */
public class ApprovalToolCallback implements ToolCallback {

  private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(5);

  /** Defense-in-depth cap on tool-result size before it reaches the model. */
  private static final int MAX_RESULT_LENGTH = 100_000;

  private final ToolCallback delegate;
  private final ApprovalRegistry registry;

  public ApprovalToolCallback(ToolCallback delegate, ApprovalRegistry registry) {
    this.delegate = delegate;
    this.registry = registry;
  }

  @Override
  public @NonNull ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
  }

  @Override
  public @NonNull ToolMetadata getToolMetadata() {
    return delegate.getToolMetadata();
  }

  @Override
  public @NonNull String call(@NonNull String toolInput) {
    return call(toolInput, null);
  }

  @Override
  public @NonNull String call(@NonNull String toolInput, ToolContext toolContext) {
    StreamSession session = ToolApprovalContext.current();
    ToolDefinition def = getToolDefinition();

    // No interactive stream (e.g. the non-streaming endpoints): there is no
    // channel to ask the user for approval, so fail closed rather than
    // silently executing the tool.
    if (session == null) {
      return "Tool '"
          + def.name()
          + "' requires interactive user approval, which is not "
          + "available on this endpoint. Do not call it again; explain that you cannot "
          + "complete the request without it.";
    }

    String id = UUID.randomUUID().toString();

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("type", "approval");
    request.put("id", id);
    request.put("tool", def.name());
    request.put("description", def.description() == null ? "" : def.description());
    request.put("arguments", toolInput == null ? "{}" : toolInput);
    session.send(request);

    boolean approved = registry.awaitApproval(id, APPROVAL_TIMEOUT);

    if (approved) {
      session.send(
          Map.of("type", "status", "text", "\n\n_✅ Approved — running `" + def.name() + "`…_\n\n"));
      return truncate(delegate.call(toolInput, toolContext));
    }

    session.send(
        Map.of(
            "type", "status", "text", "\n\n_🚫 Tool `" + def.name() + "` was not approved._\n\n"));
    return "The user denied permission to run the tool '"
        + def.name()
        + "'. Do not call it again; explain that you cannot complete the request without it.";
  }

  /**
   * Bounds the size of a tool result before it reaches the model, so a slow or hostile server
   * cannot exhaust the model's context window by returning an unbounded response.
   */
  private static String truncate(String result) {
    if (result == null || result.length() <= MAX_RESULT_LENGTH) {
      return result;
    }
    int omitted = result.length() - MAX_RESULT_LENGTH;
    return result.substring(0, MAX_RESULT_LENGTH)
        + "\n... [truncated, "
        + omitted
        + " characters omitted]";
  }
}
