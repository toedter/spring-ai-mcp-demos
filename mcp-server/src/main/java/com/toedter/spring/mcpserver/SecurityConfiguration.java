package com.toedter.spring.mcpserver;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!stdio")
public class SecurityConfiguration {

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String issuerUri;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.GET, "/**")
                    .hasAuthority("SCOPE_mcp.tools")
                    .requestMatchers(HttpMethod.POST, "/**")
                    .hasAuthority("SCOPE_mcp.tools")
                    .anyRequest()
                    .authenticated())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(withDefaults())
                    // RFC 9728 OAuth 2.0 Protected Resource Metadata, served at
                    // /.well-known/oauth-protected-resource/**, and advertised via the
                    // "resource_metadata" parameter of the WWW-Authenticate header on 401s.
                    // This lets MCP clients like the MCP Inspector discover the authorization
                    // server and the scopes to request.
                    .protectedResourceMetadata(
                        metadata ->
                            metadata.protectedResourceMetadataCustomizer(
                                builder ->
                                    builder
                                        .authorizationServer(issuerUri)
                                        .resourceName("Demo MCP Server")
                                        .tlsClientCertificateBoundAccessTokens(false)
                                        .scope("mcp.tools")
                                        .scope("mcp.tools.movies")
                                        .scope("mcp.tools.weather")
                                        .scope("mcp.tools.diagnostics"))));
    return http.build();
  }

  /** Browser-based MCP clients (MCP Inspector UI on port 6274) need CORS for discovery. */
  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:6274", "http://127.0.0.1:6274"));
    configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setExposedHeaders(
        List.of("WWW-Authenticate", "Mcp-Session-Id", "Mcp-Protocol-Version"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
