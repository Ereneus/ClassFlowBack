package com.ohiggins.classflow.academic.security;

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
 * Exige un JWT valido (verificado contra ms-auth) en todo endpoint salvo salud/docs,
 * y restringe por rol: escritura academica para docentes/administradores y las notas
 * de un estudiante solo para el, su apoderado, docentes y administradores.
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

    private static final String ADMIN = "ADMINISTRATOR";
    private static final String TEACHER = "TEACHER";

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
                // Notas de un estudiante: el propio estudiante, su apoderado, docentes y administradores.
                .requestMatchers(HttpMethod.GET, "/api/grades/student/{studentId}").access(studentAccess)
                // Resto de notas (listados completos y escritura): solo docentes y administradores.
                .requestMatchers("/api/grades", "/api/grades/**").hasAnyRole(ADMIN, TEACHER)
                // Cursos, asignaturas y evaluaciones son catalogo: lectura para cualquier usuario autenticado.
                .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/**", "/api/subjects", "/api/subjects/**",
                        "/api/evaluations", "/api/evaluations/**").authenticated()
                .requestMatchers("/api/evaluations", "/api/evaluations/**").hasAnyRole(ADMIN, TEACHER)
                .requestMatchers("/api/courses", "/api/courses/**", "/api/subjects", "/api/subjects/**").hasRole(ADMIN)
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
