package com.toedter.spring.mcpclient;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates human-in-the-loop tool approvals. A tool call blocks awaiting a
 * decision (keyed by a generated approval id) that arrives via the
 * {@code /api/chat/approve} endpoint.
 */
@Component
public class ApprovalRegistry {

    private final Map<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    /** Block until the decision for {@code id} arrives or the timeout elapses (then deny). */
    public boolean await(String id, Duration timeout) {
        CompletableFuture<Boolean> future = pending.computeIfAbsent(id, k -> new CompletableFuture<>());
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        } finally {
            pending.remove(id);
        }
    }

    /** Called by the approval endpoint to unblock a waiting tool call. */
    public void complete(String id, boolean approved) {
        pending.computeIfAbsent(id, k -> new CompletableFuture<>()).complete(approved);
    }
}

