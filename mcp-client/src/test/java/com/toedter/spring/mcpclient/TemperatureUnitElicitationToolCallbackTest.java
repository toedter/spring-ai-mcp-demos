package com.toedter.spring.mcpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;

class TemperatureUnitElicitationToolCallbackTest {

  private static final ToolDefinition WEATHER_DEFINITION =
      DefaultToolDefinition.builder()
          .name("get_weather_forecast_by_location")
          .description("Get the current weather forecast for a specific location.")
          .inputSchema("{}")
          .build();

  private static final ToolDefinition OTHER_DEFINITION =
      DefaultToolDefinition.builder()
          .name("some_other_tool")
          .description("Some other tool")
          .inputSchema("{}")
          .build();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @AfterEach
  void clearContext() {
    ToolApprovalContext.clear();
  }

  @Test
  void passesThroughUnmodifiedForNonWeatherTools() {
    ToolCallback delegate = mock(ToolCallback.class);
    when(delegate.getToolDefinition()).thenReturn(OTHER_DEFINITION);
    when(delegate.call(any(), any())).thenReturn("result");

    TemperatureUnitElicitationToolCallback callback =
        new TemperatureUnitElicitationToolCallback(delegate, new ApprovalRegistry());

    String result = callback.call("{}", null);

    assertThat(result).isEqualTo("result");
    verify(delegate).call("{}", null);
  }

  @Test
  void passesThroughUnmodifiedWhenUnitAlreadyPresent() {
    ToolCallback delegate = mock(ToolCallback.class);
    when(delegate.getToolDefinition()).thenReturn(WEATHER_DEFINITION);
    when(delegate.call(any(), any())).thenReturn("result");

    TemperatureUnitElicitationToolCallback callback =
        new TemperatureUnitElicitationToolCallback(delegate, new ApprovalRegistry());

    String input = "{\"latitude\":48.1,\"longitude\":11.5,\"unit\":\"fahrenheit\"}";
    String result = callback.call(input, null);

    assertThat(result).isEqualTo("result");
    verify(delegate).call(input, null);
  }

  @Test
  void passesThroughUnmodifiedWhenNoInteractiveSessionIsActive() {
    ToolCallback delegate = mock(ToolCallback.class);
    when(delegate.getToolDefinition()).thenReturn(WEATHER_DEFINITION);
    when(delegate.call(any(), any())).thenReturn("result");

    TemperatureUnitElicitationToolCallback callback =
        new TemperatureUnitElicitationToolCallback(delegate, new ApprovalRegistry());

    String input = "{\"latitude\":48.1,\"longitude\":11.5}";
    String result = callback.call(input, null);

    assertThat(result).isEqualTo("result");
    verify(delegate).call(input, null);
  }

  @Test
  void asksForUnitAndInjectsChoiceWhenSessionIsActive() throws Exception {
    ToolCallback delegate = mock(ToolCallback.class);
    when(delegate.getToolDefinition()).thenReturn(WEATHER_DEFINITION);
    when(delegate.call(any(), any())).thenReturn("result");

    ApprovalRegistry registry = new ApprovalRegistry();
    LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
    sink.asFlux().subscribe(events::add);
    StreamSession session = new StreamSession(sink, objectMapper);

    TemperatureUnitElicitationToolCallback callback =
        new TemperatureUnitElicitationToolCallback(delegate, registry);

    String input = "{\"latitude\":48.1,\"longitude\":11.5}";

    CompletableFuture<String> callResult =
        CompletableFuture.supplyAsync(
            () -> {
              ToolApprovalContext.set(session);
              try {
                return callback.call(input, null);
              } finally {
                ToolApprovalContext.clear();
              }
            });

    String firstEvent = events.poll(5, TimeUnit.SECONDS);
    JsonNode elicitation = objectMapper.readTree(firstEvent);
    assertThat(elicitation.get("type").asText()).isEqualTo("elicitation");

    registry.complete(elicitation.get("id").asText(), "fahrenheit");

    String result = callResult.get(5, TimeUnit.SECONDS);
    assertThat(result).isEqualTo("result");
    verify(delegate)
        .call(eq("{\"latitude\":48.1,\"longitude\":11.5,\"unit\":\"fahrenheit\"}"), any());
  }

  @Test
  void unparsableInputIsPassedThroughUnmodified() {
    ToolCallback delegate = mock(ToolCallback.class);
    when(delegate.getToolDefinition()).thenReturn(WEATHER_DEFINITION);
    when(delegate.call(any(), any())).thenReturn("result");

    TemperatureUnitElicitationToolCallback callback =
        new TemperatureUnitElicitationToolCallback(delegate, new ApprovalRegistry());

    String result = callback.call("not json", null);

    assertThat(result).isEqualTo("result");
    verify(delegate).call("not json", null);
  }
}
