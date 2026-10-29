package com.ohiggins.classflow.auth.controller;

import com.ohiggins.classflow.auth.dto.*;
import com.ohiggins.classflow.auth.service.AuthService;
import com.ohiggins.classflow.auth.service.PasswordResetService;
import com.ohiggins.classflow.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Expone los endpoints REST para autenticacion y gestion de usuarios en modo local.
 * Con el perfil entra, EntraAuthController atiende /me, /validate y las consultas de usuarios.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Profile("!entra")
@Tag(name = "Autenticación", description = "Login, registro, validación de token y gestión de usuarios")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    /**
     * Autentica un usuario y devuelve un token JWT.
     *
     * @param request credenciales de inicio de sesion.
     * @return respuesta HTTP con el token generado.
     */
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Autentica un usuario y retorna un token JWT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas")
    })
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * @param request datos de registro del usuario.
     * @return respuesta HTTP con el usuario creado.
     */
    @PostMapping("/register")
    @Operation(summary = "Registrar usuario", description = "Crea un nuevo usuario en el sistema")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente"),
        @ApiResponse(responseCode = "409", description = "Email o RUT ya registrado")
    })
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return new ResponseEntity<>(authService.register(request), HttpStatus.CREATED);
    }

    /**
     * Solicita el restablecimiento de contrasena mediante un correo con un enlace de un solo uso.
     *
     * @param request correo de la cuenta.
     * @return respuesta HTTP generica, exista o no la cuenta con ese correo.
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Solicitar restablecimiento de contraseña",
            description = "Envía un correo con un enlace de recuperación si la cuenta existe")
    @ApiResponse(responseCode = "200", description = "Solicitud procesada")
    public ResponseEntity<MessageResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.ok(new MessageResponseDTO("Si el correo está registrado, recibirás un enlace de recuperación."));
    }

    /**
     * Restablece la contrasena a partir de un token valido enviado por correo.
     *
     * @param request token recibido por correo y nueva contrasena.
     * @return respuesta HTTP con el resultado del restablecimiento.
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña", description = "Cambia la contraseña usando el token enviado por correo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña restablecida"),
        @ApiResponse(responseCode = "400", description = "Token inválido o expirado")
    })
    public ResponseEntity<MessageResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponseDTO("Contraseña actualizada correctamente."));
    }

    /**
     * Valida un token JWT y retorna los datos del usuario.
     *
     * @param token encabezado Authorization con el token.
     * @return respuesta HTTP con el usuario autenticado.
     */
    @GetMapping("/validate")
    @Operation(summary = "Validar token JWT", description = "Verifica si el token es válido y retorna los datos del usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token válido"),
        @ApiResponse(responseCode = "401", description = "Token inválido o expirado")
    })
    public ResponseEntity<UserResponseDTO> validate(
            @RequestHeader("Authorization") String token, Authentication authentication) {
        // Spring Security ya autentico el token (local, o de Entra en el login hibrido) y lo resolvio
        // al usuario interno. No se usa getCredentials(): ProviderManager las borra tras autenticar.
        if (authentication != null && authentication.isAuthenticated()) {
            return ResponseEntity.ok(resolveAuthenticatedUser(authentication));
        }
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return ResponseEntity.ok(authService.validateToken(token));
    }

    /**
     * Devuelve el perfil interno correspondiente a la autenticacion actual.
     *
     * @param authentication identidad resuelta por Spring Security.
     * @return perfil interno del usuario autenticado.
     */
    @GetMapping("/me")
    @Operation(summary = "Obtener usuario actual", description = "Devuelve el perfil interno del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario autenticado"),
        @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
        @ApiResponse(responseCode = "404", description = "Perfil interno no encontrado")
    })
    public ResponseEntity<UserResponseDTO> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(resolveAuthenticatedUser(authentication));
    }

    /**
     * Perfil interno del usuario autenticado, sea con token local o de Entra. Un token de Entra
     * sin cuenta ClassFlow habilitada llega con el Jwt como principal (ver EntraUserAuthenticationConverter).
     */
    private UserResponseDTO resolveAuthenticatedUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt) {
            throw new AccessDeniedException("La cuenta de Microsoft no está habilitada en ClassFlow.");
        }
        return userService.findByEmail(authentication.getName());
    }

    /**
     * Lista usuarios, opcionalmente filtrados por rol y curso (p. ej. los estudiantes de un curso).
     *
     * @param role rol a filtrar (ADMINISTRATOR, TEACHER, STUDENT, GUARDIAN).
     * @param course nombre del curso a filtrar.
     * @return respuesta HTTP con los usuarios encontrados.
     */
    @GetMapping("/users")
    @Operation(summary = "Listar usuarios", description = "Filtra por rol y/o curso. Solo docentes y administradores")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios"),
        @ApiResponse(responseCode = "400", description = "Rol inexistente"),
        @ApiResponse(responseCode = "403", description = "Solo docentes y administradores")
    })
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TEACHER')")
    public ResponseEntity<List<UserResponseDTO>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String course) {
        return ResponseEntity.ok(userService.findUsers(role, course));
    }

    /**
     * Activa o desactiva un usuario.
     *
     * @param id identificador del usuario.
     * @param request cuerpo con el nuevo estado: {"active": true|false}.
     * @return respuesta HTTP con el usuario actualizado.
     */
    @PutMapping("/users/{id}/active")
    @Operation(summary = "Activar o desactivar usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "403", description = "Solo administradores")
    })
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponseDTO> setUserActive(
            @PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        return ResponseEntity.ok(userService.setActive(id, Boolean.TRUE.equals(request.get("active"))));
    }

    /**
     * Obtiene un usuario por su identificador.
     *
     * @param id identificador del usuario.
     * @return respuesta HTTP con el usuario encontrado.
     */
    @GetMapping("/users/{id}")
    @Operation(summary = "Obtener usuario por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
        @ApiResponse(responseCode = "403", description = "Solo el dueño de la cuenta o un administrador pueden consultarla"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * Obtiene un usuario por su correo electronico.
     *
     * @param email correo electronico del usuario.
     * @return respuesta HTTP con el usuario encontrado.
     */
    @GetMapping("/users/email/{email}")
    @Operation(summary = "Obtener usuario por email")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TEACHER')")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.findByEmail(email));
    }

    /**
     * Obtiene un usuario por su numero de documento.
     *
     * @param idNumber numero de documento del usuario.
     * @return respuesta HTTP con el usuario encontrado.
     */
    @GetMapping("/users/idnumber/{idNumber}")
    @Operation(summary = "Obtener usuario por RUT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'TEACHER')")
    public ResponseEntity<UserResponseDTO> getUserByIdNumber(@PathVariable String idNumber) {
        return ResponseEntity.ok(userService.findByIdNumber(idNumber));
    }

    /**
     * Obtiene los estudiantes asociados a un apoderado.
     *
     * @param guardianId identificador del apoderado.
     * @return respuesta HTTP con la lista de estudiantes.
     */
    @GetMapping("/users/guardian/{guardianId}")
    @Operation(summary = "Obtener estudiantes por ID del apoderado")
    @ApiResponse(responseCode = "200", description = "Lista de estudiantes asignados al apoderado")
    @PreAuthorize("#guardianId == authentication.principal.id or hasAnyRole('ADMINISTRATOR', 'TEACHER')")
    public ResponseEntity<List<UserResponseDTO>> getStudentsByGuardian(@PathVariable Long guardianId) {
        return ResponseEntity.ok(userService.findByGuardianId(guardianId));
    }

    /**
     * Actualiza un usuario existente.
     *
     * @param id identificador del usuario.
     * @param request datos actualizados del usuario.
     * @return respuesta HTTP con el usuario actualizado.
     */
    @PutMapping("/users/{id}")
    @Operation(summary = "Actualizar usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
        @ApiResponse(responseCode = "403", description = "Solo el dueño de la cuenta o un administrador pueden actualizarla"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequestDTO request) {
        return ResponseEntity.ok(userService.updateFromRequest(id, request));
    }

    /**
     * Elimina logicamente un usuario.
     *
     * @param id identificador del usuario.
     * @return respuesta HTTP sin contenido.
     */
    @DeleteMapping("/users/{id}")
    @Operation(summary = "Eliminar usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Usuario eliminado"),
        @ApiResponse(responseCode = "403", description = "Solo el dueño de la cuenta o un administrador pueden eliminarla"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia la contraseña del usuario autenticado.
     *
     * @param token encabezado Authorization con el token.
     * @param request datos de cambio de contraseña.
     * @return respuesta HTTP con el usuario actualizado.
     */
    @PostMapping("/change-password")
    @Operation(summary = "Cambiar contraseña")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña actualizada"),
        @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta")
    })
    public ResponseEntity<UserResponseDTO> changePassword(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody ChangePasswordRequestDTO request) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        String email = authService.validateToken(token).getEmail();
        return ResponseEntity.ok(authService.changePassword(email, request));
    }
}
