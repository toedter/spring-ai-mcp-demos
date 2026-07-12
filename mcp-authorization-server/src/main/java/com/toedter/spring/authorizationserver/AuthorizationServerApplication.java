package com.toedter.spring.authorizationserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@SpringBootApplication
public class AuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorizationServerApplication.class, args);
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsManager(SecurityProperties properties) {
        SecurityProperties.User configuredUser = properties.getUser();
        List<String> roles = configuredUser.getRoles();

        UserDetails defaultUser = User.withUsername(configuredUser.getName())
                .password(configuredUser.getPassword())
                .roles(StringUtils.toStringArray(roles))
                .build();

        // Demo end-user for the Angular chatbot: john@doe.com / john
        UserDetails johnDoe = User.withUsername("john@doe.com")
                .password("{noop}john")
                .authorities("SCOPE_mcp.tools")
                .build();

        return new InMemoryUserDetailsManager(defaultUser, johnDoe);
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            context.getClaims().audience(Collections.singletonList("mcp-audience"));

            // Enrich the OIDC ID token with profile claims so the SPA can show the user.
            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                String username = context.getPrincipal().getName();
                if ("john@doe.com".equals(username)) {
                    context.getClaims()
                            .claim("name", "John Doe")
                            .claim("given_name", "John")
                            .claim("family_name", "Doe")
                            .claim("email", "john@doe.com");
                }
            }
        };
    }
}
