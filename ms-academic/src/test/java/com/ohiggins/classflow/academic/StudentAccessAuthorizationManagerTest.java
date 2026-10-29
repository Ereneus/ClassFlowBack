package com.ohiggins.classflow.academic;

import com.ohiggins.classflow.academic.security.AuthenticatedUser;
import com.ohiggins.classflow.academic.security.StudentAccessAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAccessAuthorizationManagerTest {

    // Puerto sin servicio: la consulta de pupilos a ms-auth falla y se niega el acceso.
    private final StudentAccessAuthorizationManager manager =
            new StudentAccessAuthorizationManager("http://localhost:1");

    @Test
    void studentCanSeeOwnData() {
        assertThat(isGranted(new AuthenticatedUser(24L, "a@x.cl", "STUDENT"), "24")).isTrue();
    }

    @Test
    void studentCannotSeeAnotherStudentsData() {
        assertThat(isGranted(new AuthenticatedUser(24L, "a@x.cl", "STUDENT"), "10")).isFalse();
    }

    @Test
    void teacherAndAdministratorCanSeeAnyStudent() {
        assertThat(isGranted(new AuthenticatedUser(2L, "t@x.cl", "TEACHER"), "10")).isTrue();
        assertThat(isGranted(new AuthenticatedUser(1L, "admin@x.cl", "ADMINISTRATOR"), "10")).isTrue();
    }

    @Test
    void guardianIsDeniedWhenPupilsCannotBeVerified() {
        assertThat(isGranted(new AuthenticatedUser(30L, "g@x.cl", "GUARDIAN"), "10")).isFalse();
    }

    @Test
    void invalidStudentIdIsDenied() {
        assertThat(isGranted(new AuthenticatedUser(1L, "admin@x.cl", "ADMINISTRATOR"), "abc")).isFalse();
    }

    private boolean isGranted(AuthenticatedUser user, String studentId) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        var context = new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of("studentId", studentId));
        return manager.check(() -> authentication, context).isGranted();
    }
}
