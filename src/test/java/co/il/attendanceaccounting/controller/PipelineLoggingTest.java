package co.il.attendanceaccounting.controller;

import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class PipelineLoggingTest extends BaseApiControllerTest {

    private static final String WRONG_PASSWORD = "wrong-password-never-logged";

    private static String userUrl(Integer tenantId, Integer idUser) {
        return "/account/tenant/" + tenantId + "/user/" + idUser;
    }

    private static String roleUrl(Integer tenantId, Integer idUser, String role) {
        return userUrl(tenantId, idUser) + "/role/" + role;
    }

    private static String strategyLine(CapturedOutput output, String strategy, String message) {
        return output.getOut().lines()
                .filter(line -> line.contains(strategy) && line.contains(message))
                .findFirst()
                .orElse("");
    }

    @Test
    @FlywayTest
    @DisplayName("A strategy that changes data writes one INFO 'completed' line with the caller and the target user")
    void completedChangeIsLoggedTest(CapturedOutput output) {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(ADMIN_TENANT_ID, USER_ID, "Administrator"), null,
                ADMIN_ID, ADMIN_PWD);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String line = strategyLine(output, "AddRoleService", "completed");
        assertTrue(line.contains(" INFO "), "Reason: a change is logged at INFO, line: " + line);
        assertTrue(line.contains("[user=" + ADMIN_ID + " tenant=" + ADMIN_TENANT_ID + " target=" + USER_ID + "]"),
                "Reason: the line shows who acted and on whom, line: " + line);
    }

    @Test
    @FlywayTest
    @DisplayName("A business rejection writes one WARN line with the reason the caller got")
    void businessRejectionIsLoggedTest(CapturedOutput output) {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, ADMIN_ID, "ADMINISTRATOR"),
                null, ADMIN_ID, ADMIN_PWD);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String line = strategyLine(output, "RemoveRoleService", "rejected: tenant must keep at least one administrator");
        assertTrue(line.contains(" WARN "), "Reason: a business rejection is logged at WARN, line: " + line);
        assertTrue(line.contains("target=" + ADMIN_ID + "]"), "Reason: the line names the target user, line: " + line);
    }

    @Test
    @FlywayTest
    @DisplayName("A failed login writes a WARN line naming the user id that was tried, never the password")
    void failedLoginIsLoggedWithoutPasswordTest(CapturedOutput output) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/account/login",
                createLoginRequestDto(USER_ID, WRONG_PASSWORD, USER_TENANT_ID, null), null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        String line = strategyLine(output, "LoginService", "rejected: Invalid credentials");
        assertTrue(line.contains(" WARN ") && line.contains("target=" + USER_ID + "]"),
                "Reason: repeated failures for one user id must be visible, line: " + line);
        assertFalse(output.getOut().contains(WRONG_PASSWORD), "Reason: a password is never logged");
    }

    @Test
    @FlywayTest
    @DisplayName("A read strategy also writes its INFO 'completed' line with the target user")
    void completedReadIsLoggedTest(CapturedOutput output) {
        ResponseEntity<String> response = send(HttpMethod.GET, "/account/tenant/" + ADMIN_TENANT_ID + "/userId/" + USER_ID,
                null, ADMIN_ID, ADMIN_PWD);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String line = strategyLine(output, "GetUserService", "completed");
        assertTrue(line.contains(" INFO ") && line.contains("target=" + USER_ID + "]"),
                "Reason: every strategy run is logged at INFO, line: " + line);
    }
}
