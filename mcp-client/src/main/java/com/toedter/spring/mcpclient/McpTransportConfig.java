package com.toedter.spring.mcpclient;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Attaches an OAuth2 bearer token to every outgoing MCP streamable-http
 * request, so the mcp-client can talk to the OAuth2-secured mcp-server
 * (which requires the {@code SCOPE_mcp.tools} authority on every request,
 * including the initial {@code initialize}/{@code listTools} calls).
 * <p>
 * When a chat request is in flight for a signed-in end user (see
 * {@link CurrentUserToken}), the user's access token is exchanged (RFC 8693
 * Token Exchange, see {@link TokenExchangeService}) for a delegated token
 * that keeps the user's {@code sub} claim while adding mcp-client as the
 * {@code act}or claim. Otherwise (e.g. keep-alive pings, stdio mode)
 * mcp-client's own client-credentials service token is used, as before.
 */
@Configuration
public class McpTransportConfig {

    @Bean
    public McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> mcpBearerTokenCustomizer(
            McpServiceTokenProvider tokenProvider, TokenExchangeService tokenExchangeService) {
        return (serverName, builder) -> builder.httpRequestCustomizer(
                (requestBuilder, method, uri, body, context) -> requestBuilder.header("Authorization",
                        "Bearer " + resolveAccessToken(serverName, tokenProvider, tokenExchangeService)));
    }

    private static String resolveAccessToken(String serverName, McpServiceTokenProvider tokenProvider,
                                              TokenExchangeService tokenExchangeService) {
        String serviceToken = tokenProvider.getAccessToken(serverName);
        String userAccessToken = CurrentUserToken.current();
        if (userAccessToken == null) {
            return serviceToken;
        }
        try {
            return tokenExchangeService.exchangeUserToken(serverName, userAccessToken, serviceToken);
        } catch (Exception ex) {
            // Fall back to the plain service token if the exchange fails, so a
            // temporary authorization-server hiccup doesn't break tool calls.
            return serviceToken;
        }
    }
}

