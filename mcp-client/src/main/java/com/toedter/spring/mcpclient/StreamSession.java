package com.toedter.spring.mcpclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import reactor.core.publisher.Sinks;

/**
 * Represents the active Server-Sent-Events stream for a single chat request. The tool-approval
 * wrapper uses it to push events (approval requests, status updates) to the connected chatbot while
 * the tool call is blocked.
 */
public class StreamSession {

  private final Sinks.Many<String> sink;
  private final ObjectMapper mapper;

  public StreamSession(Sinks.Many<String> sink, ObjectMapper mapper) {
    this.sink = sink;
    this.mapper = mapper;
  }

  /** Serialise an event to JSON and emit it as one SSE message. */
  public void send(Map<String, Object> event) {
    try {
      sink.tryEmitNext(mapper.writeValueAsString(event));
    } catch (Exception e) {
      sink.tryEmitNext("{\"type\":\"error\",\"message\":\"serialization failed\"}");
    }
  }
}
