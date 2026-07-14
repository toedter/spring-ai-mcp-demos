/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.toedter.spring.mcpserver;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Performs an OAuth 2.0 Token Exchange (RFC 8693) against
 * mcp-authorization-server so mcp-server can add itself as an additional
 * {@code act}or on top of the delegated token it received from mcp-client
 * (which already carries {@code sub}=end-user, {@code act.sub}=mcp-client).
 * <p>
 * The authorization server preserves the existing actor chain and appends the
 * new actor, so the resulting token looks like:
 * <pre>
 * {
 *   "sub": "john@doe.com",
 *   "act": {
 *     "sub": "mcp-server-client",
 *     "act": { "sub": "mcp-client-client" }
 *   }
 * }
 * </pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 -
 * OAuth 2.0 Token Exchange</a>
 */
@Service
public class TokenService {

    private static final String TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    /** mcp-server's own client-credentials access token (the "actor_token"), cached until it expires. */
    private volatile CachedToken cachedActorToken;

    public TokenService(
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
     * Exchanges the access token used to call this MCP tool for a new token
     * that adds mcp-server as the actor, and returns the exchanged token's
     * decoded JWT claims (pretty-printed JSON) so it can be inspected.
     */
    @McpTool(name = "get_mcp_server_access_token",
            description = "Exchanges the current request's access token so that mcp-server is added as an actor, "
                    + "and returns the decoded JWT claims of the exchanged access token.")
    public String getMcpServerAccessToken() {
        String incomingAccessToken = currentAccessToken();
        if (incomingAccessToken == null) {
            return "No authenticated access token available for the current request.";
        }
        String exchangedAccessToken = exchangeToken(incomingAccessToken);
        return decodeJwtClaimsPretty(exchangedAccessToken);
    }

    /**
     * Exchanges {@code subjectAccessToken} (typically the token mcp-client
     * used to call mcp-server, itself already delegated on behalf of an end
     * user) for a new access token with mcp-server added as the actor.
     */
    public String exchangeToken(String subjectAccessToken) {
        String actorToken = getOwnAccessToken();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", TOKEN_EXCHANGE_GRANT_TYPE);
        form.add("subject_token", subjectAccessToken);
        form.add("subject_token_type", ACCESS_TOKEN_TYPE);
        form.add("actor_token", actorToken);
        form.add("actor_token_type", ACCESS_TOKEN_TYPE);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        // Without an explicit scope, the authorization server falls back to
        // authorizing all of the subject token's scopes (openid, profile,
        // mcp.tools from the upstream user/mcp-client exchange), which fails
        // with invalid_scope because mcp-server-client is only registered for
        // mcp.tools. Requesting the scope explicitly limits validation to
        // what this client actually needs and is registered for.
        form.add("scope", scope);

        Map<String, Object> response = postForm(form);
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Token exchange endpoint " + tokenUri + " returned no access_token");
        }
        return (String) response.get("access_token");
    }

    /** Returns (and caches) mcp-server's own client-credentials access token, used as the {@code actor_token}. */
    private synchronized String getOwnAccessToken() {
        CachedToken token = this.cachedActorToken;
        if (token == null || !token.isValid()) {
            token = fetchOwnAccessToken();
            this.cachedActorToken = token;
        }
        return token.accessToken();
    }

    private CachedToken fetchOwnAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", scope);

        Map<String, Object> response = postForm(form);
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Token endpoint " + tokenUri + " returned no access_token");
        }
        String accessToken = (String) response.get("access_token");
        Number expiresIn = response.get("expires_in") instanceof Number n ? n : 300;
        // Refresh a little early to avoid races with in-flight requests.
        Instant expiry = Instant.now().plusSeconds(Math.max(0, expiresIn.longValue() - 30));
        return new CachedToken(accessToken, expiry);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(MultiValueMap<String, String> form) {
        return restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
    }

    /** Returns the raw access token used to authenticate the current MCP request, or {@code null}. */
    private String currentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        return null;
    }

    private String decodeJwtClaimsPretty(String jwt) {
        String[] parts = jwt.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        Object claims = objectMapper.readValue(payload, Object.class);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(claims);
    }

    private record CachedToken(String accessToken, Instant expiry) {
        boolean isValid() {
            return accessToken != null && Instant.now().isBefore(expiry);
        }
    }
}

