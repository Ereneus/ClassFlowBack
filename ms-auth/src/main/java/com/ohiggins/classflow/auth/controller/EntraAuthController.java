package com.ohiggins.classflow.auth.controller;

import com.ohiggins.classflow.auth.dto.UserResponseDTO;
import com.ohiggins.classflow.auth.entity.User;
import com.ohiggins.classflow.auth.service.ExternalIdentityService;
import com.ohiggins.classflow.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Endpoints de perfil y validación de identidad bajo Microsoft Entra ID. */
@RestController
@RequestMapping("/api/auth")
@Profile("entra")
@RequiredArgsConstructor
public class EntraAuthController {

    private final ExternalIdentityService externalIdentityService;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(resolveCurrentUser(authentication));
    }

    @GetMapping("/validate")
    public ResponseEntity<UserResponseDTO> validate(Authentication authentication) {
        return ResponseEntity.ok(resolveCurrentUser(authentication));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id, Authentication authentication) {
        UserResponseDTO currentUser = resolveCurrentUser(authentication);
        if (!id.equals(currentUser.getId()) && !"ADMINISTRATOR".equals(currentUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar este perfil.");
        }
        return ResponseEntity.ok(userService.findById(id));
    }

    @GetMapping("/users/guardian/{guardianId}")
    public ResponseEntity<List<UserResponseDTO>> getStudentsByGuardian(
            @PathVariable Long guardianId, Authentication authentication) {
        UserResponseDTO currentUser = resolveCurrentUser(authentication);
        if (!guardianId.equals(currentUser.getId()) && !"ADMINISTRATOR".equals(currentUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar estos estudiantes.");
        }
        return ResponseEntity.ok(userService.findByGuardianId(guardianId));
    }

    private UserResponseDTO resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere un access token de Entra ID.");
        }

        String tenantId = jwt.getClaimAsString("tid");
        String objectId = jwt.getClaimAsString("oid");
        if (tenantId == null || tenantId.isBlank() || objectId == null || objectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token no contiene tid y oid.");
        }

        String email = jwt.getClaimAsString("preferred_username");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("email");
        }

        User user;
        try {
            user = externalIdentityService.resolveExistingUser("ENTRA", tenantId, objectId, email);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta de Microsoft no está habilitada en ClassFlow.");
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta ClassFlow está desactivada.");
        }
        return userService.convertToDTO(user);
    }
}