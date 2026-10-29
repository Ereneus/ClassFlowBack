package com.ohiggins.classflow.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Valida access tokens de Microsoft Entra ID cuando el perfil entra esta activo.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("entra")
public class EntraSecurityConfig {

    private final String issuerUri;
    private final String audience;

    public EntraSecurityConfig(
            @Value("${entra.issuer-uri}") String issuerUri,
            @Value("${entra.audience}") String audience) {
        this.issuerUri = issuerUri;
        this.audience = audience;
    }

    @Bean
    public SecurityWebFilterChain entraSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder entraJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        // Los tokens v2 traen el client ID como aud; los v1 traen el Application ID URI (api://...).
        String clientId = audience.startsWith("api://") ? audience.substring("api://".length()) : audience;
        List<String> acceptedAudiences = List.of(clientId, "api://" + clientId);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            Collection<String> audiences = jwt.getAudience();
            if (audiences != null && audiences.stream().anyMatch(acceptedAudiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "El token no contiene el audience esperado.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> scopes = jwt.getClaimAsStringList("roles");
            String scopeValue = jwt.getClaimAsString("scp");
            if (scopeValue != null) {
                scopes = new java.util.ArrayList<>(scopes == null ? List.of() : scopes);
                scopes.addAll(Arrays.stream(scopeValue.split(" ")).filter(scope -> !scope.isBlank()).toList());
            }
            if (scopes == null) {
                return List.<GrantedAuthority>of();
            }
            return scopes.stream()
                    .map(scope -> (GrantedAuthority) new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            scope.startsWith("ROLE_") ? scope : "SCOPE_" + scope))
                    .toList();
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}