package com.toedter.spring.mcpclient;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the {@link StreamSession} of the currently active streaming chat request, so the tool-call
 * approval handler can reach the active SSE stream. This is deliberately NOT a {@link ThreadLocal}:
 * the MCP SDK invokes tool-call callbacks on its own transport-listener thread, not the {@code
 * boundedElastic} worker thread on which {@link ChatController} sets the session — a plain
 * thread-local lookup would always see {@code null} there.
 *
 * <p>Active sessions are tracked on a stack, so nested/concurrent requests resolve to the most
 * recently started one still in flight. For a single-user demo like this one that's exactly right;
 * a multi-tenant deployment would need to correlate tool-call callbacks with their originating
 * request explicitly (the MCP Java SDK does not currently pass that correlation into the callback).
 */
public final class ToolApprovalContext {

  private static final Deque<StreamSession> ACTIVE = new ArrayDeque<>();

  private ToolApprovalContext() {}

  public static synchronized void set(StreamSession session) {
    ACTIVE.push(session);
  }

  public static synchronized StreamSession current() {
    return ACTIVE.peek();
  }

  public static synchronized void clear() {
    ACTIVE.poll();
  }
}
