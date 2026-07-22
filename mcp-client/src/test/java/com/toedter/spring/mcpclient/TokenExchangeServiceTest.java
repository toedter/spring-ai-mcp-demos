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
 * Verifies delegated (exchanged) tokens are cached per {@code (serverName, subjectAccessToken)}
 * rather than globally, so the same user's exchanged token is never reused across MCP server
 * connections.
 */
class TokenExchangeServiceTest {

  private HttpServer server;
  private final AtomicInteger requestCount = new AtomicInteger();
  private TokenExchangeService tokenExchangeService;

  @BeforeEach
  void startFakeTokenEndpoint() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/oauth2/token",
        exchange -> {
          requestCount.incrementAndGet();
          String body =
              "{\"access_token\":\"exchanged-" + requestCount.get() + "\",\"expires_in\":300}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();

    String tokenUri = "http://localhost:" + server.getAddress().getPort() + "/oauth2/token";
    tokenExchangeService = new TokenExchangeService(tokenUri, "client-id", "client-secret");
  }

  @AfterEach
  void stopFakeTokenEndpoint() {
    server.stop(0);
  }

  @Test
  void cachesExchangedTokenPerServerNameForTheSameSubjectToken() {
    String userToken = "user-access-token";
    String actorToken = "actor-access-token";

    String exchangedForServerA1 =
        tokenExchangeService.exchangeUserToken("server-a", userToken, actorToken);
    String exchangedForServerA2 =
        tokenExchangeService.exchangeUserToken("server-a", userToken, actorToken);
    String exchangedForServerB =
        tokenExchangeService.exchangeUserToken("server-b", userToken, actorToken);

    // Same server + same subject token: cached, one round-trip.
    assertThat(exchangedForServerA1).isEqualTo(exchangedForServerA2);
    // Same subject token but a different server: independent delegated
    // token, never reused across servers.
    assertThat(exchangedForServerB).isNotEqualTo(exchangedForServerA1);
    assertThat(requestCount.get()).isEqualTo(2);
  }
}
