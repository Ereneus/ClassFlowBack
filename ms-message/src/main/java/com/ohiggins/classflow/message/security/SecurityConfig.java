package com.ohiggins.classflow.message.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.function.Supplier;

/**
 * Exige un JWT valido (verificado contra ms-auth) en todo endpoint salvo salud/docs.
 * Cada usuario solo accede a sus propios mensajes; los avisos los publican docentes y administradores.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health",
        "/actuator/info",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/h2-console/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // Bandejas: cada usuario solo las suyas (el administrador, todas).
                .requestMatchers(HttpMethod.GET, "/api/messages/receiver/{userId}",
                        "/api/messages/receiver/{userId}/unread", "/api/messages/sender/{userId}")
                    .access(SecurityConfig::selfOrAdmin)
                .requestMatchers(HttpMethod.GET, "/api/messages").hasRole("ADMINISTRATOR")
                // Avisos: los lee cualquiera; los publican y borran docentes y administradores.
                .requestMatchers(HttpMethod.GET, "/api/announcements", "/api/announcements/**").authenticated()
                .requestMatchers("/api/announcements", "/api/announcements/**").hasAnyRole("ADMINISTRATOR", "TEACHER")
                // Enviar, marcar como leido y borrar: MessageController valida remitente/receptor.
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static AuthorizationDecision selfOrAdmin(
            Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        if (!(authentication.get().getPrincipal() instanceof AuthenticatedUser user)) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(
                user.isAdmin() || String.valueOf(user.id()).equals(context.getVariables().get("userId")));
    }
}
