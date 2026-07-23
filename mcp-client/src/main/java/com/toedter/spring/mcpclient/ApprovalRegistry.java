package com.toedter.spring.mcpclient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Coordinates human-in-the-loop tool-call approval decisions before a server-initiated action
 * proceeds. Each pending decision is registered under a generated id; the caller blocks on {@link
 * #await} while the decision arrives asynchronously (from the browser) via one of {@link
 * ChatController}'s decision endpoints, which calls {@link #complete}.
 */
@Component
public class ApprovalRegistry {

  private final Map<String, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

  /**
   * Block until the decision for {@code id} arrives or the timeout elapses, returning {@code
   * onTimeout} in the latter case (fail closed).
   */
  @SuppressWarnings("unchecked")
  public <T> T await(String id, Duration timeout, T onTimeout) {
    CompletableFuture<Object> future = pending.computeIfAbsent(id, k -> new CompletableFuture<>());
    try {
      return (T) future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      return onTimeout;
    } finally {
      pending.remove(id);
    }
  }

  /** Convenience for the common approve/deny case (tool calls). */
  public boolean awaitApproval(String id, Duration timeout) {
    return await(id, timeout, Boolean.FALSE);
  }

  /** Called by a decision endpoint to unblock a waiting request. */
  public void complete(String id, Object decision) {
    pending.computeIfAbsent(id, k -> new CompletableFuture<>()).complete(decision);
  }
}
