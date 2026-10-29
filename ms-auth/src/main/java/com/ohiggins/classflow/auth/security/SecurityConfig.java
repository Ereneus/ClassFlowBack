package com.ohiggins.classflow.auth.security;

import com.ohiggins.classflow.auth.service.ExternalIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

/**
 * Configura la seguridad HTTP y los componentes basicos de autenticacion.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Profile("!entra")
public class SecurityConfig {

    /** Endpoints POST de autenticacion accesibles sin token, porque el usuario aun no tiene uno en ese punto. */
    private static final String[] PUBLIC_AUTH_POST_ENDPOINTS = {
        "/api/auth/login",
        "/api/auth/forgot-password",
        "/api/auth/reset-password"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ExternalIdentityService externalIdentityService;

    /** Con issuer y audience de Entra definidos, el modo local tambien acepta tokens de Microsoft (login hibrido). */
    @Value("${entra.issuer-uri:}")
    private String entraIssuerUri;

    @Value("${entra.audience:}")
    private String entraAudience;

    /**
     * Define la cadena de filtros y reglas de acceso.
     *
     * @param http configuracion HTTP de Spring Security.
     * @return cadena de filtros de seguridad.
     * @throws Exception si la configuracion falla.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POST_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").hasRole("ADMINISTRATOR")
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (StringUtils.hasText(entraIssuerUri) && StringUtils.hasText(entraAudience)) {
            // Lazy: el discovery de Entra se resuelve en el primer token, no al arrancar.
            JwtDecoder entraDecoder = new SupplierJwtDecoder(
                    () -> EntraSecurityConfig.buildEntraJwtDecoder(entraIssuerUri, entraAudience));
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .bearerTokenResolver(skipWhenAlreadyAuthenticated())
                    .jwt(jwt -> jwt
                            .decoder(entraDecoder)
                            .jwtAuthenticationConverter(new EntraUserAuthenticationConverter(externalIdentityService))));
        }

        return http.build();
    }

    /**
     * JwtAuthenticationFilter corre antes y autentica los tokens locales; en ese caso no se
     * intenta validarlos de nuevo como tokens de Entra (fallarian y responderian 401).
     */
    private BearerTokenResolver skipWhenAlreadyAuthenticated() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> SecurityContextHolder.getContext().getAuthentication() != null
                ? null
                : delegate.resolve(request);
    }

    /**
     * Expone el AuthenticationManager de Spring Security.
     *
     * @param config configuracion de autenticacion.
     * @return manager de autenticacion.
     * @throws Exception si no puede resolverse.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configura el encoder de contrasenas usado por el servicio.
     *
     * @return encoder BCrypt.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}