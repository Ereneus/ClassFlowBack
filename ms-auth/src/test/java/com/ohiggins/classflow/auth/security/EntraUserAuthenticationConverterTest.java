package com.ohiggins.classflow.auth.security;

import com.ohiggins.classflow.auth.entity.Role;
import com.ohiggins.classflow.auth.entity.User;
import com.ohiggins.classflow.auth.service.ExternalIdentityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntraUserAuthenticationConverterTest {

    @Mock
    private ExternalIdentityService externalIdentityService;

    @InjectMocks
    private EntraUserAuthenticationConverter converter;

    @Test
    void linkedActiveUserBecomesInternalPrincipalWithRole() {
        Jwt jwt = jwt("preferred_username", "teacher@classflow.cl");
        User user = user(true);
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user);

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getCredentials()).isSameAs(jwt);
        assertThat(authentication.getName()).isEqualTo("teacher@classflow.cl");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_TEACHER");
    }

    @Test
    void v1TokenFallsBackToUpnClaim() {
        Jwt jwt = jwt("upn", "teacher@classflow.cl");
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user(true));

        assertThat(converter.convert(jwt)).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    }

    @Test
    void unknownIdentityStaysAsJwtWithoutRoles() {
        Jwt jwt = jwt("preferred_username", "unknown@classflow.cl");
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "unknown@classflow.cl"))
                .thenThrow(new IllegalArgumentException("No existe"));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(authentication.getPrincipal()).isSameAs(jwt);
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void inactiveUserStaysAsJwtWithoutRoles() {
        Jwt jwt = jwt("preferred_username", "teacher@classflow.cl");
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user(false));

        assertThat(converter.convert(jwt)).isInstanceOf(JwtAuthenticationToken.class);
    }

    private Jwt jwt(String emailClaim, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("tid", "tenant-id")
                .claim("oid", "object-id")
                .claim(emailClaim, email)
                .build();
    }

    private User user(boolean active) {
        User user = new User();
        user.setId(7L);
        user.setEmail("teacher@classflow.cl");
        user.setRole(Role.TEACHER);
        user.setActive(active);
        return user;
    }
}
