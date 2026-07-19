package com.toedter.spring.mcpclient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Fetches (and caches) an OAuth2 client-credentials access token used to
 * authenticate outgoing MCP transport traffic (initialize, listTools,
 * callTool, keep-alive pings, ...) against the mcp-server, which is secured
 * as an OAuth2 resource server requiring the {@code mcp.tools} scope.
 * <p>
 * Uses the same {@code mcp-client-client} client-credentials registration as
 * {@code mcp-authorization-server/get-access-token.http}.
 * <p>
 * Tokens are cached per MCP server connection name so that a token is never
 * reused across servers, even though this demo only configures one.
 */
@Component
public class McpServiceTokenProvider {

    private final RestClient restClient = RestClient.create();
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    private final Map<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();

    public McpServiceTokenProvider(
            @Value("${mcp.service-token.token-uri}") String tokenUri,
            @Value("${mcp.service-token.client-id}") String clientId,
            @Value("${mcp.service-token.client-secret}") String clientSecret,
            @Value("${mcp.service-token.scope}") String scope) {
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    /**
     * Returns a valid access token scoped to {@code serverName}, fetching (or
     * refreshing) it as needed. Each MCP server connection gets its own
     * cached token so a token is never reused across servers.
     */
    public synchronized String getAccessToken(String serverName) {
        CachedToken token = this.cachedTokens.get(serverName);
        if (token == null || !token.isValid()) {
            token = fetchToken();
            this.cachedTokens.put(serverName, token);
        }
        return token.accessToken();
    }

    @SuppressWarnings("unchecked")
    private CachedToken fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", scope);

        Map<String, Object> response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Token endpoint " + tokenUri + " returned no access_token");
        }
        String accessToken = (String) response.get("access_token");
        Number expiresIn = response.get("expires_in") instanceof Number n ? n : 300;
        // Refresh a little early to avoid races with in-flight requests.
        Instant expiry = Instant.now().plusSeconds(Math.max(0, expiresIn.longValue() - 30));
        return new CachedToken(accessToken, expiry);
    }

    private record CachedToken(String accessToken, Instant expiry) {
        boolean isValid() {
            return accessToken != null && Instant.now().isBefore(expiry);
        }
    }
}

