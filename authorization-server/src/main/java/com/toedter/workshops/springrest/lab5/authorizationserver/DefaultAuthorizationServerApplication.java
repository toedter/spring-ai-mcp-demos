package com.toedter.workshops.springrest.lab5.authorizationserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@SpringBootApplication
public class DefaultAuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DefaultAuthorizationServerApplication.class, args);
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsManager(SecurityProperties properties) {
        SecurityProperties.User user = properties.getUser();
        List<String> roles = user.getRoles();
        return new InMemoryUserDetailsManager(User.withUsername(user.getName())
                .password(user.getPassword())
                .roles(StringUtils.toStringArray(roles))
                .build());
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> context.getClaims().audience(Collections.singletonList("mcp-audience"));
    }
}
