package com.toedter.spring.mcpclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Wraps the weather tool so that, if the model didn't already specify a preferred temperature unit,
 * the chatbot user is asked for one before the tool runs. This mirrors the user-facing behavior of
 * MCP elicitation (see the <a
 * href="https://modelcontextprotocol.io/specification/draft/client/elicitation">elicitation
 * spec</a>) as a client-side UX pattern: mcp-server runs in {@code STATELESS} mode, and the
 * installed MCP Java SDK does not support the server-initiated {@code elicitation/create}
 * round-trip (classic or the newer stateless multi-round-trip form) for stateless tool methods, so
 * this fills the same need without relying on the wire protocol.
 */
public class TemperatureUnitElicitationToolCallback implements ToolCallback {

  private static final String WEATHER_TOOL_NAME = "get_weather_forecast_by_location";
  private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);
  private static final String DEFAULT_UNIT = "celsius";

  private final ToolCallback delegate;
  private final ApprovalRegistry registry;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TemperatureUnitElicitationToolCallback(ToolCallback delegate, ApprovalRegistry registry) {
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
    if (!WEATHER_TOOL_NAME.equals(getToolDefinition().name())) {
      return delegate.call(toolInput, toolContext);
    }

    ObjectNode arguments = parseArguments(toolInput);
    if (arguments == null || hasUnit(arguments)) {
      // The model already picked a unit, or the arguments couldn't be
      // parsed as a JSON object: run the tool as-is either way.
      return delegate.call(toolInput, toolContext);
    }

    StreamSession session = ToolApprovalContext.current();
    if (session == null) {
      // No interactive channel to ask the user: unlike tool-call approval,
      // a temperature-unit preference isn't security sensitive, so fail
      // open and let the tool fall back to its own default.
      return delegate.call(toolInput, toolContext);
    }

    String id = UUID.randomUUID().toString();
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "elicitation");
    event.put("id", id);
    event.put("message", "Which temperature unit do you prefer?");
    event.put("options", List.of("celsius", "fahrenheit"));
    session.send(event);

    String unit = registry.await(id, RESPONSE_TIMEOUT, DEFAULT_UNIT);
    session.send(Map.of("type", "status", "text", "\n\n_🌡️ Using " + unit + "…_\n\n"));

    arguments.put("unit", unit);
    return delegate.call(arguments.toString(), toolContext);
  }

  private ObjectNode parseArguments(String toolInput) {
    try {
      return (ObjectNode) objectMapper.readTree(toolInput == null ? "{}" : toolInput);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean hasUnit(ObjectNode arguments) {
    return arguments.hasNonNull("unit") && !arguments.get("unit").asText().isBlank();
  }
}
