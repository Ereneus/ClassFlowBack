package com.ohiggins.classflow.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seguridad para tokens emitidos por Microsoft Entra ID.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
    public SecurityFilterChain entraSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register",
                                "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/change-password")
                        .denyAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtDecoder entraJwtDecoder() {
        return buildEntraJwtDecoder(issuerUri, audience);
    }

    /**
     * Decoder que valida firma, issuer y audience de un access token de Entra ID.
     * Lo reutiliza SecurityConfig para el login hibrido (local + Entra).
     */
    static JwtDecoder buildEntraJwtDecoder(String issuerUri, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
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

    /**
     * Entra emite el mismo tenant con dos issuers segun la version del access token
     * (accessTokenAcceptedVersion de la API): v1 = sts.windows.net/{tid}/ y
     * v2 = login.microsoftonline.com/{tid}/v2.0. Se aceptan ambos para el tenant configurado.
     */
    static Set<String> acceptedIssuers(String issuerUri) {
        Matcher matcher = TENANT_ID.matcher(issuerUri);
        if (!matcher.find()) {
            return Set.of(issuerUri);
        }
        String tenantId = matcher.group();
        // Set.copyOf (no Set.of) porque issuerUri suele coincidir con una de las dos formas.
        return Set.copyOf(List.of(
                issuerUri,
                "https://sts.windows.net/" + tenantId + "/",
                "https://login.microsoftonline.com/" + tenantId + "/v2.0"));
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        role.startsWith("ROLE_") ? role : "ROLE_" + role)));
            }
            String scopeValue = jwt.getClaimAsString("scp");
            if (scopeValue != null) {
                Arrays.stream(scopeValue.split(" "))
                        .filter(scope -> !scope.isBlank())
                        .forEach(scope -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope)));
            }
            return authorities;
        });
        return converter;
    }
}