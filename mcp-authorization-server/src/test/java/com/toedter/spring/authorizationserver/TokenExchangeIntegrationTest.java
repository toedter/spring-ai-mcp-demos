package com.toedter.spring.authorizationserver;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the RFC 8693 OAuth 2.0 Token Exchange grant configured for
 * mcp-auth-client: exchanging a (simulated) end-user access token together
 * with mcp-client's own client-credentials access token yields a new access
 * token that keeps the original user's {@code sub} claim while adding
 * mcp-client as the {@code act} (actor) claim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenExchangeIntegrationTest {

    private static final String TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    @LocalServerPort
    private int port;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    @Test
    @SuppressWarnings("unchecked")
    void tokenExchangeKeepsUserSubjectAndAddsMcpClientAsActor() throws Exception {
        String tokenUri = "http://localhost:" + port + "/oauth2/token";

        // ----- 1. Simulate a signed-in end user by saving an authorization
        // for the mcp-chatbot-client with a real Principal attribute (as the
        // authorization_code flow would have done after a browser login). -----
        String subjectTokenValue = "test-subject-token-" + System.currentTimeMillis();
        saveUserAuthorization("john@doe.com", subjectTokenValue);

        // ----- 2. Get mcp-client's own client-credentials access token
        // (the "actor" in the delegation). -----
        MultiValueMap<String, String> clientCredentialsForm = new LinkedMultiValueMap<>();
        clientCredentialsForm.add("grant_type", "client_credentials");
        clientCredentialsForm.add("client_id", "mcp-auth-client");
        clientCredentialsForm.add("client_secret", "secret");
        clientCredentialsForm.add("scope", "mcp.tools");

        Map<String, Object> clientCredentialsResponse = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(clientCredentialsForm)
                .retrieve()
                .body(Map.class);
        assertThat(clientCredentialsResponse).isNotNull();
        String actorToken = (String) clientCredentialsResponse.get("access_token");
        assertThat(actorToken).isNotBlank();

        // ----- 3. Exchange the user's (subject) token, using mcp-client's own
        // token as the actor token. -----
        MultiValueMap<String, String> tokenExchangeForm = new LinkedMultiValueMap<>();
        tokenExchangeForm.add("grant_type", TOKEN_EXCHANGE_GRANT_TYPE);
        tokenExchangeForm.add("subject_token", subjectTokenValue);
        tokenExchangeForm.add("subject_token_type", ACCESS_TOKEN_TYPE);
        tokenExchangeForm.add("actor_token", actorToken);
        tokenExchangeForm.add("actor_token_type", ACCESS_TOKEN_TYPE);
        tokenExchangeForm.add("client_id", "mcp-auth-client");
        tokenExchangeForm.add("client_secret", "secret");

        Map<String, Object> exchangeResponse = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(tokenExchangeForm)
                .retrieve()
                .body(Map.class);
        assertThat(exchangeResponse).isNotNull();
        String exchangedAccessToken = (String) exchangeResponse.get("access_token");
        assertThat(exchangedAccessToken).isNotBlank();

        // ----- 4. Decode the exchanged JWT's claims and assert the
        // delegation semantics: sub = original user, act.sub = mcp-client. -----
        JsonNode claims = decodeJwtClaims(exchangedAccessToken);
        assertThat(claims.get("sub").asString()).isEqualTo("john@doe.com");
        assertThat(claims.has("act")).isTrue();
        assertThat(claims.get("act").get("sub").asString()).isEqualTo("mcp-auth-client");
    }

    private void saveUserAuthorization(String username, String tokenValue) {
        RegisteredClient chatbotClient = registeredClientRepository.findByClientId("mcp-chatbot-client");
        assertThat(chatbotClient).isNotNull();

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(300);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue,
                issuedAt, expiresAt, Set.of("openid", "profile", "mcp.tools"));

        Authentication principal = new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority("SCOPE_mcp.tools")));

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(chatbotClient)
                .principalName(username)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "profile", "mcp.tools"))
                .token(accessToken)
                .attribute(Principal.class.getName(), principal)
                .build();

        authorizationService.save(authorization);
    }

    private JsonNode decodeJwtClaims(String jwt) {
        String[] parts = jwt.split("\\.");
        byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readTree(payload);
    }
}

