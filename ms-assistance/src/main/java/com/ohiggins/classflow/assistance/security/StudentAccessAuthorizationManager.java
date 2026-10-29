package com.ohiggins.classflow.assistance.security;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Autoriza el acceso a los datos de un estudiante ({studentId} en la ruta): el propio
 * estudiante, su apoderado (segun ms-auth), docentes y administradores.
 */
public class StudentAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final RestClient restClient;

    public StudentAccessAuthorizationManager(String authServiceUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().baseUrl(authServiceUrl).requestFactory(factory).build();
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Long studentId = parseId(context.getVariables().get("studentId"));
        if (studentId == null || !(authentication.get().getPrincipal() instanceof AuthenticatedUser user)) {
            return new AuthorizationDecision(false);
        }

        boolean granted = switch (user.role()) {
            case "ADMINISTRATOR", "TEACHER" -> true;
            case "STUDENT" -> studentId.equals(user.id());
            case "GUARDIAN" -> isGuardianOf(user.id(), studentId,
                    context.getRequest().getHeader(HttpHeaders.AUTHORIZATION));
            default -> false;
        };
        return new AuthorizationDecision(granted);
    }

    /** Pregunta a ms-auth por los pupilos del apoderado con el mismo token de la peticion. */
    private boolean isGuardianOf(Long guardianId, Long studentId, String authorizationHeader) {
        try {
            List<Map<String, Object>> students = restClient.get()
                    .uri("/api/auth/users/guardian/{guardianId}", guardianId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            return students != null && students.stream()
                    .map(student -> student.get("id"))
                    .filter(Objects::nonNull)
                    .anyMatch(id -> studentId.equals(((Number) id).longValue()));
        } catch (RestClientException | ClassCastException e) {
            return false;
        }
    }

    private Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
