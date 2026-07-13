package com.toedter.spring.mcpclient;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Attaches an OAuth2 bearer token (client-credentials grant, see
 * {@link McpServiceTokenProvider}) to every outgoing MCP streamable-http
 * request, so the mcp-client can talk to the OAuth2-secured mcp-server
 * (which requires the {@code SCOPE_mcp.tools} authority on every request,
 * including the initial {@code initialize}/{@code listTools} calls).
 */
@Configuration
public class McpTransportConfig {

    @Bean
    public McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> mcpBearerTokenCustomizer(
            McpServiceTokenProvider tokenProvider) {
        return (serverName, builder) -> builder.httpRequestCustomizer(
                (requestBuilder, method, uri, body, context) ->
                        requestBuilder.header("Authorization", "Bearer " + tokenProvider.getAccessToken()));
    }
}

