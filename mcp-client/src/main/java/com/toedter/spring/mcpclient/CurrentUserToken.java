package com.toedter.spring.mcpclient;

/**
 * Holds the raw OAuth2 access token of the signed-in end user for the duration of a chat request,
 * so it can be used as the {@code subject_token} when exchanging it (see {@link
 * TokenExchangeService}) for a delegated token that mcp-client presents to the mcp-server on the
 * user's behalf.
 *
 * <p>This is needed in addition to Spring Security's request-bound {@code SecurityContextHolder}
 * because the streaming chat endpoint executes the actual model/tool calls on a worker thread (see
 * {@code Schedulers.boundedElastic()} in {@link ChatController}) where the original request's
 * security context is not automatically propagated.
 */
public final class CurrentUserToken {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private CurrentUserToken() {}

  public static void set(String token) {
    CURRENT.set(token);
  }

  public static String current() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
