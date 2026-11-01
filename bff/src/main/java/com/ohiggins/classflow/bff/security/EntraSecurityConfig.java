package com.ohiggins.classflow.bff.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validacion local de access tokens emitidos por Microsoft Entra ID.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("entra")
public class EntraSecurityConfig {

    private static final Pattern TENANT_ID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

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
                        .pathMatchers("/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder entraJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build();
        Set<String> acceptedIssuers = acceptedIssuers(issuerUri);
        OAuth2TokenValidator<Jwt> issuerValidator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<String>(JwtClaimNames.ISS, acceptedIssuers::contains));
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

    static Set<String> acceptedIssuers(String issuerUri) {
        Matcher matcher = TENANT_ID.matcher(issuerUri);
        if (!matcher.find()) {
            return Set.of(issuerUri);
        }
        String tenantId = matcher.group();
        return Set.copyOf(List.of(
                issuerUri,
                "https://sts.windows.net/" + tenantId + "/",
                "https://login.microsoftonline.com/" + tenantId + "/v2.0"));
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new java.util.ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(
                        role.startsWith("ROLE_") ? role : "ROLE_" + role)));
            }
            String scopeValue = jwt.getClaimAsString("scp");
            if (scopeValue != null) {
                java.util.Arrays.stream(scopeValue.split(" "))
                        .filter(scope -> !scope.isBlank())
                        .forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
            }
            return authorities;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}