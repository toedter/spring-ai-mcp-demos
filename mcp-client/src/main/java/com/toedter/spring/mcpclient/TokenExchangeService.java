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
 * Performs an OAuth 2.0 Token Exchange (RFC 8693) against
 * mcp-authorization-server so mcp-client can swap an end user's access token
 * for a new, delegated access token that:
 * <ul>
 *   <li>keeps the original user's {@code sub} claim (so mcp-server still
 *       knows on whose behalf the request is made), and</li>
 *   <li>adds mcp-client as the {@code act} (actor) claim (so mcp-server can
 *       also see which service actually performed the call).</li>
 * </ul>
 * The exchange uses mcp-client's own client-credentials access token (see
 * {@link McpServiceTokenProvider}) as the {@code actor_token}, and the
 * incoming user's access token as the {@code subject_token}. Both tokens are
 * sent as {@code urn:ietf:params:oauth:token-type:access_token}.
 * <p>
 * Exchanged tokens are cached per {@code (serverName, subjectAccessToken)}
 * pair so that a delegated token is never reused across MCP server
 * connections, even though this demo only configures one.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 -
 * OAuth 2.0 Token Exchange</a>
 */
@Component
public class TokenExchangeService {

    private static final String TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final RestClient restClient = RestClient.create();
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    /** Exchanged tokens are cached per (server, subject token) to avoid a round-trip per tool call. */
    private final Map<CacheKey, CachedToken> cache = new ConcurrentHashMap<>();

    public TokenExchangeService(
            @Value("${mcp.service-token.token-uri}") String tokenUri,
            @Value("${mcp.service-token.client-id}") String clientId,
            @Value("${mcp.service-token.client-secret}") String clientSecret) {
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Exchanges {@code subjectAccessToken} (the end user's access token) for a
     * new access token scoped to {@code serverName} that keeps the user's
     * {@code sub} claim while adding {@code actorAccessToken}'s owner
     * (mcp-client) as the {@code act} claim.
     */
    public String exchangeUserToken(String serverName, String subjectAccessToken, String actorAccessToken) {
        CacheKey key = new CacheKey(serverName, subjectAccessToken);
        CachedToken cached = cache.get(key);
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }
        CachedToken fresh = fetchExchangedToken(subjectAccessToken, actorAccessToken);
        cache.entrySet().removeIf(entry -> !entry.getValue().isValid());
        cache.put(key, fresh);
        return fresh.accessToken();
    }

    @SuppressWarnings("unchecked")
    private CachedToken fetchExchangedToken(String subjectAccessToken, String actorAccessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", TOKEN_EXCHANGE_GRANT_TYPE);
        form.add("subject_token", subjectAccessToken);
        form.add("subject_token_type", ACCESS_TOKEN_TYPE);
        form.add("actor_token", actorAccessToken);
        form.add("actor_token_type", ACCESS_TOKEN_TYPE);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Token exchange endpoint " + tokenUri + " returned no access_token");
        }
        String accessToken = (String) response.get("access_token");
        Number expiresIn = response.get("expires_in") instanceof Number n ? n : 300;
        // Refresh a little early to avoid races with in-flight requests.
        Instant expiry = Instant.now().plusSeconds(Math.max(0, expiresIn.longValue() - 30));
        return new CachedToken(accessToken, expiry);
    }

    private record CacheKey(String serverName, String subjectAccessToken) {
    }

    private record CachedToken(String accessToken, Instant expiry) {
        boolean isValid() {
            return accessToken != null && Instant.now().isBefore(expiry);
        }
    }
}

