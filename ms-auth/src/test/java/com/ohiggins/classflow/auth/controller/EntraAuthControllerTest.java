package com.ohiggins.classflow.auth.controller;

import com.ohiggins.classflow.auth.dto.UserResponseDTO;
import com.ohiggins.classflow.auth.entity.User;
import com.ohiggins.classflow.auth.service.ExternalIdentityService;
import com.ohiggins.classflow.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntraAuthControllerTest {

    @Mock
    private ExternalIdentityService externalIdentityService;

    @Mock
    private UserService userService;

    @InjectMocks
    private EntraAuthController controller;

    @Test
    void meResolvesUserByTenantAndObjectId() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tid", "tenant-id")
                .claim("oid", "object-id")
                .claim("preferred_username", "teacher@classflow.cl")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        User user = new User();
        UserResponseDTO response = UserResponseDTO.builder().email("teacher@classflow.cl").build();
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user);
        when(userService.convertToDTO(user)).thenReturn(response);

        assertSame(response, controller.getCurrentUser(authentication).getBody());
        verify(externalIdentityService).resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl");
    }

    @Test
    void validateReturnsTheResolvedInternalUser() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tid", "tenant-id")
                .claim("oid", "object-id")
                .claim("email", "teacher@classflow.cl")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        User user = new User();
        UserResponseDTO response = UserResponseDTO.builder().id(12L).role("TEACHER").build();
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user);
        when(userService.convertToDTO(user)).thenReturn(response);

        assertSame(response, controller.validate(authentication).getBody());
        verify(externalIdentityService).resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl");
    }

    @Test
    void userCanReadTheirOwnProfileForTheBff() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tid", "tenant-id")
                .claim("oid", "object-id")
                .claim("email", "teacher@classflow.cl")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        User user = new User();
        user.setId(12L);
        UserResponseDTO response = UserResponseDTO.builder().id(12L).role("TEACHER").build();
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user);
        when(userService.convertToDTO(user)).thenReturn(response);
        when(userService.findById(12L)).thenReturn(response);

        assertSame(response, controller.getUserById(12L, authentication).getBody());
    }

    @Test
    void userCannotReadAnotherUsersProfile() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tid", "tenant-id")
                .claim("oid", "object-id")
                .claim("email", "teacher@classflow.cl")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        User user = new User();
        user.setId(12L);
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user);
        when(userService.convertToDTO(user)).thenReturn(UserResponseDTO.builder().id(12L).role("TEACHER").build());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.getUserById(13L, authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void unlinkedMicrosoftAccountIsForbidden() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tid", "tenant-id")
                .claim("oid", "object-id")
                .claim("email", "unknown@classflow.cl")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "unknown@classflow.cl"))
                .thenThrow(new IllegalArgumentException("No existe un usuario interno para la identidad externa."));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.getCurrentUser(authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void disabledInternalUserCannotUseEntraProfile() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tid", "tenant-id")
                .claim("oid", "object-id")
                .claim("email", "teacher@classflow.cl")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        User user = new User();
        user.setActive(false);
        when(externalIdentityService.resolveExistingUser("ENTRA", "tenant-id", "object-id", "teacher@classflow.cl"))
                .thenReturn(user);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.getCurrentUser(authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }
}