package com.toedter.spring.mcpclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies tokens are cached per MCP server connection name (guideline: never reuse a token across
 * servers), by counting how many times the token endpoint is actually hit.
 */
class McpServiceTokenProviderTest {

  private HttpServer server;
  private final AtomicInteger requestCount = new AtomicInteger();
  private McpServiceTokenProvider tokenProvider;

  @BeforeEach
  void startFakeTokenEndpoint() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/oauth2/token",
        exchange -> {
          requestCount.incrementAndGet();
          String body =
              "{\"access_token\":\"token-" + requestCount.get() + "\",\"expires_in\":300}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();

    String tokenUri = "http://localhost:" + server.getAddress().getPort() + "/oauth2/token";
    tokenProvider =
        new McpServiceTokenProvider(tokenUri, "client-id", "client-secret", "mcp.tools");
  }

  @AfterEach
  void stopFakeTokenEndpoint() {
    server.stop(0);
  }

  @Test
  void cachesTokenPerServerNameRatherThanGlobally() {
    String tokenForServerA1 = tokenProvider.getAccessToken("server-a");
    String tokenForServerA2 = tokenProvider.getAccessToken("server-a");
    String tokenForServerB = tokenProvider.getAccessToken("server-b");

    // Same server name: cached, so only one HTTP round-trip.
    assertThat(tokenForServerA1).isEqualTo(tokenForServerA2);
    // Different server name: independent token, never reused across servers.
    assertThat(tokenForServerB).isNotEqualTo(tokenForServerA1);
    assertThat(requestCount.get()).isEqualTo(2);
  }
}
