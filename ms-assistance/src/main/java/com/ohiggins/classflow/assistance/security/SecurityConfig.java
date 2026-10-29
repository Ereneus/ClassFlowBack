package com.ohiggins.classflow.assistance.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Exige un JWT valido (verificado contra ms-auth) en todo endpoint salvo salud/docs.
 * Registrar asistencia y anotaciones es de docentes/administradores; los datos de un
 * estudiante solo los ven el, su apoderado, docentes y administradores.
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

    @Value("${auth.service.url}")
    private String authServiceUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        StudentAccessAuthorizationManager studentAccess = new StudentAccessAuthorizationManager(authServiceUrl);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // Asistencia y anotaciones de un estudiante: el propio estudiante, su apoderado, docentes y administradores.
                .requestMatchers(HttpMethod.GET, "/api/attendance/student/{studentId}",
                        "/api/annotations/student/{studentId}", "/api/annotations/student/{studentId}/type/{type}")
                    .access(studentAccess)
                // Listados completos, por curso y toda escritura: solo docentes y administradores.
                .requestMatchers("/api/attendance", "/api/attendance/**", "/api/annotations", "/api/annotations/**")
                    .hasAnyRole("ADMINISTRATOR", "TEACHER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
