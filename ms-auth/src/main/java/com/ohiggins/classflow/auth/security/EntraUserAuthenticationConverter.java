package com.ohiggins.classflow.auth.security;

import com.ohiggins.classflow.auth.entity.User;
import com.ohiggins.classflow.auth.exception.DuplicateResourceException;
import com.ohiggins.classflow.auth.service.ExternalIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Traduce un access token de Microsoft Entra ID al usuario interno de ClassFlow, para que
 * el resto del servicio (/validate, /me, @PreAuthorize) lo trate igual que un login local:
 * principal = User y authorities = ROLE_ del rol interno.
 */
@RequiredArgsConstructor
public class EntraUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ExternalIdentityService externalIdentityService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            User user = externalIdentityService.resolveExistingUser(
                    "ENTRA", jwt.getClaimAsString("tid"), jwt.getClaimAsString("oid"), resolveEmail(jwt));
            if (Boolean.TRUE.equals(user.getActive())) {
                return new UsernamePasswordAuthenticationToken(user, jwt, user.getAuthorities());
            }
        } catch (IllegalArgumentException | DuplicateResourceException e) {
            // Identidad sin cuenta ClassFlow vinculable: se resuelve abajo como token sin roles.
        }
        // Token de Microsoft valido pero sin cuenta ClassFlow habilitada: autenticado sin roles,
        // y AuthController responde 403 en /me y /validate.
        return new JwtAuthenticationToken(jwt, List.of());
    }

    /** Los tokens v2 traen preferred_username; los v1 (issuer sts.windows.net) traen upn. */
    private String resolveEmail(Jwt jwt) {
        for (String claim : List.of("preferred_username", "email", "upn")) {
            String value = jwt.getClaimAsString(claim);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
