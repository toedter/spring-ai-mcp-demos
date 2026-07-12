package com.toedter.spring.mcpclient;

/**
 * Holds the {@link StreamSession} for the current worker thread so the
 * tool-approval callback can reach the active SSE stream. It is set by the
 * streaming chat endpoint before invoking the model and cleared afterwards.
 */
public final class ToolApprovalContext {

    private static final ThreadLocal<StreamSession> CURRENT = new ThreadLocal<>();

    private ToolApprovalContext() {
    }

    public static void set(StreamSession session) {
        CURRENT.set(session);
    }

    public static StreamSession current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

