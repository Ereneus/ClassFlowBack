package com.ohiggins.classflow.analytics.security;

/**
 * Datos minimos del usuario autenticado, resueltos por ms-auth vía /api/auth/validate.
 */
public record AuthenticatedUser(Long id, String email, String role) {
}
