package co.il.attendanceaccounting.controller;

import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import co.il.attendanceaccounting.AttendanceAccountingApplication;
import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.dto.LoginResponseDto;
import co.il.attendanceaccounting.model.User;

import static co.il.attendanceaccounting.security.SecurityConstants.SYSTEM_TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountControllerTest extends BaseApiControllerTest {

    private static final String ACCOUNT_URL = "/account";
    private static final String USER_URL = ACCOUNT_URL + "/user";
    private static final String LOGIN_URL = ACCOUNT_URL + "/login";
    private static final String TENANT_URL = ACCOUNT_URL + "/tenant/";
    private static final String ROLE_URL = "/role/";
    private static final String TENANT_ADD_URL = ACCOUNT_URL + "/tenant/add";
    private static final Integer NEW_TENANT_ID = 300;
    private static final Integer PROTECTED_ADMIN_ID = 123456789;
    private static final String PROTECTED_MESSAGE = "user is protected and cannot be deleted";
    private static final String USER_ROLE = "USER";
    private static final String ADMINISTRATOR_ROLE = "ADMINISTRATOR";
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";
    private static final String NEW_USER_PWD = "pw";
    private static final String BOOTSTRAP_PWD = "test-bootstrap-password";

    @Autowired
    private AttendanceAccountingApplication application;

    @Test
    @FlywayTest
    @DisplayName("POST /account/user as the first user of an empty tenant registers them as administrator")
    void registerNewUserTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(99, NEW_USER_PWD, "New", "User", "new.user@example.com", NEW_TENANT_ID), null,
                null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(99, body.getIdUser().intValue());
        assertEquals("New", body.getFirstName());
        assertEquals("User", body.getLastName());
        assertEquals("new.user@example.com", body.getEmail());
        assertEquals(NEW_TENANT_ID, body.getTenantId());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), body.getRoles(),
                "Reason: the first user of a tenant keeps the default 'USER' role and also gets 'ADMINISTRATOR'");
        assertTrue(userRepository.existsById(99));
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), userRepository.findById(99).orElseThrow().getRoles(),
                "Reason: both roles must be persisted in user_roles, not only returned");
        assertEquals("new.user@example.com", userRepository.findById(99).orElseThrow().getEmail());
        assertEquals(NEW_TENANT_ID, userRepository.findById(99).orElseThrow().getTenantId());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user for the second user of a tenant grants only the default 'USER' role")
    void registerSecondUserInTenantTest() {
        ResponseEntity<String> first = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(99, NEW_USER_PWD, "First", "User", "first.user@example.com", NEW_TENANT_ID), null,
                null);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertTrue(readObject(first, UserProfileDto.class).getRoles().contains(ADMINISTRATOR_ROLE),
                "Reason: the first registration must be the administrator for this test to be meaningful");

        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(92, NEW_USER_PWD, "Second", "User", "second.user@example.com", NEW_TENANT_ID),
                null, null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(Set.of(USER_ROLE), body.getRoles(),
                "Reason: only the first user of a tenant is promoted, every later one stays a plain user");
        assertEquals(Set.of(USER_ROLE), userRepository.findById(92).orElseThrow().getRoles());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), userRepository.findById(99).orElseThrow().getRoles(),
                "Reason: registering a second user must not strip the first user's administrator role");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user as the first user of a tenant can then use an administrator-only endpoint")
    void firstUserOfTenantIsAuthorizedAsAdminTest() {
        ResponseEntity<String> admin = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(91, NEW_USER_PWD, "Tenant", "Admin", "tenant.admin@example.com", NEW_TENANT_ID),
                null, null);
        assertEquals(HttpStatus.CREATED, admin.getStatusCode());
        ResponseEntity<String> member = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(90, NEW_USER_PWD, "Tenant", "Member", "tenant.member@example.com", NEW_TENANT_ID),
                null, null);
        assertEquals(HttpStatus.CREATED, member.getStatusCode());

        String token = obtainToken(91, NEW_USER_PWD, NEW_TENANT_ID);
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, roleUrl(NEW_TENANT_ID, 90, "Administrator"),
                null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: the auto-granted role must survive login into the JWT authorities claim and satisfy hasRole('ADMINISTRATOR')");
        assertTrue(userRepository.findById(90).orElseThrow().getRoles().contains("Administrator"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user into a tenant whose users were all removed grants administrator again")
    void registerIntoEmptiedTenantTest() throws Exception {
        ResponseEntity<String> deleted = sendWithToken(HttpMethod.DELETE,
                userUrl(OTHER_USER_TENANT_ID, OTHER_USER_ID), null, platformAdminToken());
        assertEquals(HttpStatus.OK, deleted.getStatusCode());
        assertFalse(userRepository.existsById(OTHER_USER_ID));

        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(89, NEW_USER_PWD, "New", "Owner", "new.owner@example.com", OTHER_USER_TENANT_ID),
                null, null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), readObject(response, UserProfileDto.class).getRoles(),
                "Reason: the rule is 'no users in the tenant', so a tenant left empty gets an administrator again");
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), userRepository.findById(89).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user with an existing id returns 409 CONFLICT")
    void registerExistingUserTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL, createUserRegisterDto(USER_ID, "pw", "Dup", "User", "dup@example.com", NEW_TENANT_ID), null, null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("user exists", errorMessage(response));
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user with a malformed email returns 400 BAD_REQUEST")
    void registerWithInvalidEmailTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL, createUserRegisterDto(98, "pw", "New", "User", "not-an-email", NEW_TENANT_ID), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
        assertFalse(userRepository.existsById(98));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user without an email returns 400 BAD_REQUEST")
    void registerWithMissingEmailTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL, createUserRegisterDto(97, "pw", "New", "User", null, NEW_TENANT_ID), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
        assertFalse(userRepository.existsById(97));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user without a tenantId returns 400 BAD_REQUEST")
    void registerWithMissingTenantIdTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL, createUserRegisterDto(96, "pw", "New", "User", "new.user2@example.com", null), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
        assertFalse(userRepository.existsById(96));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user with a tenantId missing from att_ac_tenant_mapping returns 400 BAD_REQUEST")
    void registerWithUnknownTenantIdTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL, createUserRegisterDto(95, "pw", "New", "User", "new.user3@example.com", UNKNOWN_TENANT_ID), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Tenant with id = " + UNKNOWN_TENANT_ID + " not found", errorMessage(response));
        assertEquals(before, userRepository.count(), "Reason: no user should be added for an unknown tenant");
        assertFalse(userRepository.existsById(95));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user into a seeded tenant registers the user as a plain user")
    void registerWithSeededTenantIdTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(94, NEW_USER_PWD, "New", "User", "new.user4@example.com",
                TENANT_ID_100), null, null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(TENANT_ID_100, body.getTenantId());
        assertEquals(TENANT_ID_100, userRepository.findById(94).orElseThrow().getTenantId());
        assertEquals(Set.of(USER_ROLE), body.getRoles(),
                "Reason: tenant 100 already holds seeded users, so the newcomer must not be promoted");
        assertFalse(userRepository.findById(94).orElseThrow().getRoles().contains(ADMINISTRATOR_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user succeeds in a tenant added through POST /account/tenant/add")
    void registerAfterAddingTenantTest() {
        ResponseEntity<String> tenantResponse = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto("Initrode", "Bob Slydell", "+1-555-0400", "admin@initrode.local"), null, null);
        assertEquals(HttpStatus.CREATED, tenantResponse.getStatusCode());
        Integer addedTenantId = readObject(tenantResponse, TenantMappingDto.class).getTenantId();

        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(93, NEW_USER_PWD, "New", "User", "new.user5@example.com", addedTenantId), null,
                null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Reason: registration must accept a tenant that was just onboarded through the add-tenant API");
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(addedTenantId, body.getTenantId());
        assertEquals(addedTenantId, userRepository.findById(93).orElseThrow().getTenantId());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), body.getRoles(),
                "Reason: onboarding a tenant and registering into it must yield that tenant's first administrator");
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), userRepository.findById(93).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with valid credentials  returns token and profile")
    void loginValidTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, USER_TENANT_ID, null), null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponseDto body = readObject(response, LoginResponseDto.class);
        assertEquals("Bearer", body.getTokenType());
        UserProfileDto profile = body.getProfile();
        assertEquals(USER_ID,profile.getIdUser().intValue());
        assertEquals("John", profile.getFirstName());
        assertEquals("Doe", profile.getLastName());
        assertEquals(USER_EMAIL, profile.getEmail());
        assertEquals(USER_TENANT_ID, profile.getTenantId());
        assertEquals(TENANT_100_NAME, profile.getCompanyName(),
                "Reason: login must resolve the company name from att_ac_tenant_mapping");
        assertTrue(profile.getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login for a user whose tenant has no mapping row returns a profile without companyName")
    void loginUnmappedTenantTest() {
        send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(98, NEW_USER_PWD, "No", "Tenant", "no.tenant@example.com", NEW_TENANT_ID),
                null, null);
        tenantMappingRepository.deleteById(NEW_TENANT_ID);

        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(98, NEW_USER_PWD, NEW_TENANT_ID, null), null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: a missing tenant mapping must not block login");
        UserProfileDto profile = readObject(response, LoginResponseDto.class).getProfile();
        assertEquals(NEW_TENANT_ID, profile.getTenantId());
        assertNull(profile.getCompanyName(),
                "Reason: with no mapping row there is no name to report");
        assertFalse(tenantMappingRepository.existsById(NEW_TENANT_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login without credentials returns 401 UNAUTHORIZED")
    void loginAnonymousTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, createLoginRequestDto(null, null, null, null),
                null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with a wrong password returns 401 UNAUTHORIZED")
    void loginWrongPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, "wrong-password", USER_TENANT_ID, null), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with a wrong tenantId returns 401 UNAUTHORIZED")
    void loginWrongTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, OTHER_USER_TENANT_ID, null), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login without a tenantId returns 401 UNAUTHORIZED")
    void loginMissingTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, null, null), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} as admin removes a user of the admin's tenant")
    void deleteUserAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(ADMIN_TENANT_ID, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(USER_ID, body.getIdUser().intValue());
        assertEquals("John", body.getFirstName());
        assertEquals("Doe", body.getLastName());
        assertEquals(USER_EMAIL, body.getEmail());
        assertFalse(userRepository.existsById(USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} as non-admin returns 403 and keeps the user")
    void deleteUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(USER_TENANT_ID, OTHER_USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(userRepository.existsById(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} for a missing user (admin) returns 404")
    void deleteUserMissingTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(ADMIN_TENANT_ID, 987654), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("User with login = 987654 not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} for the bootstrap super-admin returns 403 even as admin, and 403 not 400 when absent")
    void deleteProtectedAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(USER_TENANT_ID, PROTECTED_ADMIN_ID), null, ADMIN_ID,
                ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: the protected id is rejected before the existence lookup, so the response never reveals whether the super-admin exists");
        assertEquals(PROTECTED_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} for the bootstrap super-admin keeps the existing row")
    void deleteProtectedAdminKeepsRowTest() {
        ResponseEntity<String> created = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(PROTECTED_ADMIN_ID, "pw", "Super", "Admin", "super.admin@example.com",
                        NEW_TENANT_ID), null, null);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(USER_TENANT_ID, PROTECTED_ADMIN_ID), null, ADMIN_ID,
                ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(PROTECTED_MESSAGE, errorMessage(response));
        assertTrue(userRepository.existsById(PROTECTED_ADMIN_ID),
                "Reason: the protected user must survive a delete attempt, so the startup bootstrap never recreates it");
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} still removes an ordinary user when a protected id exists")
    void deleteOrdinaryUserStillWorksTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(ADMIN_TENANT_ID, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(userRepository.existsById(USER_ID),
                "Reason: protection must apply to the configured id only, not to deletion in general");
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} as the same user updates the profile")
    void editUserSelfTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID), createUserEditDto("Johnny", null, null, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals("Johnny", body.getFirstName());
        assertEquals("Johnny", userRepository.findById(USER_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} for another user (non-admin) returns 403")
    void editUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, OTHER_USER_ID), createUserEditDto("Hacker", null, null, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertEquals("Jane", userRepository.findById(OTHER_USER_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} as the same user updates the email")
    void editUserEmailTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID), createUserEditDto(null, null, null, "john.new@example.com"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals("john.new@example.com", body.getEmail());
        assertEquals("john.new@example.com", userRepository.findById(USER_ID).orElseThrow().getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} with a malformed email returns 400 and keeps the original email")
    void editUserInvalidEmailTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID), createUserEditDto(null, null, null, "not-an-email"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(USER_EMAIL, userRepository.findById(USER_ID).orElseThrow().getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} for a missing user (admin) returns 404")
    void editUserMissingTest() {
        long usersBefore = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(ADMIN_TENANT_ID, 987654),
                createUserEditDto("Ghost", null, null, null), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Reason: a missing user is reported as not found, not as an unhandled server failure");
        assertEquals("User with login = 987654 not found", errorMessage(response));
        assertFalse(userRepository.existsById(987654));
        assertEquals(usersBefore, userRepository.count());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user/{id}/role/{role} as admin adds the role")
    void addRoleAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(ADMIN_TENANT_ID, USER_ID, "Administrator"), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertTrue(body.getRoles().contains("Administrator"));
        assertTrue(body.getRoles().contains("User"));
        assertTrue(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("Administrator"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user/{id}/role/{role} as non-admin returns 403")
    void addRoleForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(USER_TENANT_ID, USER_ID, "Administrator"), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertFalse(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("Administrator"));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id}/role/{role} as admin removes the role")
    void removeRoleAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, USER_ID, "User"), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertFalse(body.getRoles().contains("User"));
        assertFalse(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id}/role/{role} as non-admin returns 403")
    void removeRoleForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(USER_TENANT_ID, USER_ID, "User"), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("Bootstrap creates the platform admin in tenant 0, and its token passes administrator-only endpoints")
    void bootstrapPlatformAdminPassesAdministratorEndpointTest() throws Exception {
        application.run();
        User platformAdmin = userRepository.findById(PROTECTED_ADMIN_ID).orElseThrow();
        assertEquals(Set.of(PLATFORM_ADMIN_ROLE), platformAdmin.getRoles(),
                "Reason: the platform admin holds only PLATFORM_ADMIN, the hierarchy implies the rest");
        assertEquals(SYSTEM_TENANT_ID, platformAdmin.getTenantId());

        String token = obtainToken(PROTECTED_ADMIN_ID, BOOTSTRAP_PWD, SYSTEM_TENANT_ID);
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST,
                roleUrl(USER_TENANT_ID, USER_ID, "Administrator"), null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: PLATFORM_ADMIN must imply ADMINISTRATOR through the role hierarchy");
        assertTrue(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("Administrator"));
    }

    @Test
    @FlywayTest
    @DisplayName("Bootstrap resets an existing platform admin row to PLATFORM_ADMIN only in tenant 0")
    void bootstrapResetsPlatformAdminRolesAndTenantTest() throws Exception {
        userRepository.save(User.builder().idUser(PROTECTED_ADMIN_ID).password("irrelevant").tenantId(USER_TENANT_ID)
                .role("User").role("Administrator").build());

        application.run();

        User platformAdmin = userRepository.findById(PROTECTED_ADMIN_ID).orElseThrow();
        assertEquals(Set.of(PLATFORM_ADMIN_ROLE), platformAdmin.getRoles(),
                "Reason: startup must restore the platform admin's role set, whatever was stored before");
        assertEquals(SYSTEM_TENANT_ID, platformAdmin.getTenantId(),
                "Reason: the platform admin always lives in the system tenant");
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/users returns only that tenant's users")
    void getAllUsersTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, usersUrl(USER_TENANT_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<UserProfileDto> users = readList(response, UserProfileDto.class);
        List<Integer> ids = users.stream().map(UserProfileDto::getIdUser).toList();
        assertEquals(2, users.size());
        assertTrue(ids.containsAll(List.of(ADMIN_ID, USER_ID)));
        assertFalse(ids.contains(OTHER_USER_ID), "Reason: another tenant's users must never be listed");
        assertTrue(users.stream().allMatch(u -> USER_TENANT_ID.equals(u.getTenantId())));
        assertEquals(USER_EMAIL, users.stream().filter(u -> u.getIdUser().equals(USER_ID)).findFirst().orElseThrow().getEmail());
        assertEquals(ADMIN_EMAIL, users.stream().filter(u -> u.getIdUser().equals(ADMIN_ID)).findFirst().orElseThrow().getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/users never exposes the password field")
    void getAllUsersNoPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, usersUrl(USER_TENANT_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        json(response).forEach(node -> assertFalse(node.has("password"), "Reason: profile must not leak the password"));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/users without a token returns 401 UNAUTHORIZED")
    void getAllUsersUnauthorizedTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, usersUrl(USER_TENANT_ID), null, null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/200/users by a tenant-100 user returns 403")
    void getAllUsersByOtherTenantIdTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, usersUrl(OTHER_USER_TENANT_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: the path tenant must match the JWT tenant");
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/999/users as the platform admin returns an empty list")
    void getAllUsersUnknownTenantIdTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.GET, usersUrl(UNKNOWN_TENANT_ID), null,
                platformAdminToken());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<UserProfileDto> users = readList(response, UserProfileDto.class);
        assertTrue(users.isEmpty());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} returns the matching user")
    void getUserByTenantAndIdTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(USER_TENANT_ID, USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(USER_ID, body.getIdUser().intValue());
        assertEquals("John", body.getFirstName());
        assertEquals("Doe", body.getLastName());
        assertEquals(USER_EMAIL, body.getEmail());
        assertEquals(USER_TENANT_ID, body.getTenantId());
        assertTrue(body.getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} with a tenant other than the caller's returns 403")
    void getUserByTenantAndIdWrongTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(OTHER_USER_TENANT_ID, OTHER_USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: the path tenant must match the JWT tenant, as in TimeTracking");
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} for a user of another tenant returns 404")
    void getUserByTenantAndIdUserOfOtherTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(USER_TENANT_ID, OTHER_USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Reason: a user outside the caller's tenant must look exactly like a missing user");
        assertEquals("User with login = " + OTHER_USER_ID + " not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} for a missing user returns 404")
    void getUserByTenantAndIdMissingTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(USER_TENANT_ID, 987654), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("User with login = 987654 not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} without a token returns 401 UNAUTHORIZED")
    void getUserByTenantAndIdUnauthorizedTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(USER_TENANT_ID, USER_ID), null, null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{own}/user/{id} by an admin for a user of another tenant returns 404 and keeps the user")
    void deleteUserOfOtherTenantAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(ADMIN_TENANT_ID, OTHER_USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Reason: an admin acts only inside its own tenant, and other tenants' users look missing");
        assertEquals("User with login = " + OTHER_USER_ID + " not found", errorMessage(response));
        assertTrue(userRepository.existsById(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/200/user/{id} by a tenant-100 admin returns 403 and keeps the user")
    void deleteUserNamingOtherTenantAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE,
                userUrl(OTHER_USER_TENANT_ID, OTHER_USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: only the platform admin may name a tenant other than its JWT tenant");
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(userRepository.existsById(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{own}/user/password/{id} by an admin for a user of another tenant returns 404 and changes nothing")
    void editUserOfOtherTenantAsAdminTest() {
        String passwordBefore = userRepository.findById(OTHER_USER_ID).orElseThrow().getPassword();
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(ADMIN_TENANT_ID, OTHER_USER_ID),
                createUserEditDto("Hacked", null, "new-password", null), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        User other = userRepository.findById(OTHER_USER_ID).orElseThrow();
        assertEquals("Jane", other.getFirstName());
        assertEquals(passwordBefore, other.getPassword(),
                "Reason: an admin of another tenant must not be able to reset this user's password");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{own}/user/{id}/role/{role} by an admin for a user of another tenant returns 404 and keeps the roles")
    void addRoleOfOtherTenantAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(ADMIN_TENANT_ID, OTHER_USER_ID, "Administrator"),
                null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(Set.of("User"), userRepository.findById(OTHER_USER_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{own}/user/{id}/role/{role} by an admin for a user of another tenant returns 404 and keeps the roles")
    void removeRoleOfOtherTenantAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, OTHER_USER_ID, "User"),
                null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(Set.of("User"), userRepository.findById(OTHER_USER_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/200/user/{id} as the platform admin removes that tenant's user")
    void deleteUserAsPlatformAdminTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.DELETE,
                userUrl(OTHER_USER_TENANT_ID, OTHER_USER_ID), null, platformAdminToken());
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: the platform admin may act on any tenant it names");
        assertFalse(userRepository.existsById(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{t}/user/{id} as the platform admin naming the wrong tenant returns 404 and keeps the user")
    void deleteUserAsPlatformAdminWrongTenantTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.DELETE,
                userUrl(USER_TENANT_ID, OTHER_USER_ID), null, platformAdminToken());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Reason: the user is looked up inside the named tenant only");
        assertTrue(userRepository.existsById(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/0/user/password/{id} lets the platform admin edit its own profile")
    void editSelfAsPlatformAdminTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.PUT,
                passwordUrl(SYSTEM_TENANT_ID, PROTECTED_ADMIN_ID),
                createUserEditDto("Support", null, null, null), platformAdminToken());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Support", userRepository.findById(PROTECTED_ADMIN_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/200/users as the platform admin returns that tenant's users")
    void getAllUsersAsPlatformAdminTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.GET, usersUrl(OTHER_USER_TENANT_ID),
                null, platformAdminToken());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<UserProfileDto> users = readList(response, UserProfileDto.class);
        assertEquals(1, users.size());
        assertEquals(OTHER_USER_ID, users.get(0).getIdUser());
        assertEquals(OTHER_USER_TENANT_ID, users.get(0).getTenantId());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} as the platform admin returns a user of any tenant")
    void getUserAsPlatformAdminTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.GET, userByTenantUrl(OTHER_USER_TENANT_ID, OTHER_USER_ID),
                null, platformAdminToken());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(OTHER_USER_ID, readObject(response, UserProfileDto.class).getIdUser());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/0/user/password/{platformAdmin} by a tenant admin returns 403 and keeps the platform admin")
    void editPlatformAdminAsTenantAdminTest() throws Exception {
        application.run();
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(SYSTEM_TENANT_ID, PROTECTED_ADMIN_ID),
                createUserEditDto("Owned", null, "attacker-password", null), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: tenant 0 is the system tenant, only the platform admin's own JWT carries it");
        assertEquals("Platform", userRepository.findById(PROTECTED_ADMIN_ID).orElseThrow().getFirstName());
        assertFalse(obtainToken(PROTECTED_ADMIN_ID, BOOTSTRAP_PWD, SYSTEM_TENANT_ID).isBlank(),
                "Reason: the platform admin's password must be unchanged");
    }

    private String platformAdminToken() throws Exception {
        application.run();
        return obtainToken(PROTECTED_ADMIN_ID, BOOTSTRAP_PWD, SYSTEM_TENANT_ID);
    }

    private String userByTenantUrl(Integer tenantId, Integer idUser) {
        return TENANT_URL + tenantId + "/userId/" + idUser;
    }

    private String userUrl(Integer tenantId, Integer idUser) {
        return TENANT_URL + tenantId + "/user/" + idUser;
    }

    private String passwordUrl(Integer tenantId, Integer idUser) {
        return TENANT_URL + tenantId + "/user/password/" + idUser;
    }

    private String roleUrl(Integer tenantId, Integer idUser, String role) {
        return userUrl(tenantId, idUser) + ROLE_URL + role;
    }

    private String usersUrl(Integer tenantId) {
        return TENANT_URL + tenantId + "/users";
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login identifies the organization by company name instead of its id")
    void loginByCompanyNameTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, null, TENANT_100_NAME), null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: a caller who knows the company name should not need to know its id");
        UserProfileDto profile = readObject(response, LoginResponseDto.class).getProfile();
        assertEquals(USER_TENANT_ID, profile.getTenantId(),
                "Reason: the name must resolve to the organization the user belongs to");
        assertEquals(TENANT_100_NAME, profile.getCompanyName());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login matches the company name regardless of case and surrounding spaces")
    void loginByCompanyNameIgnoresCaseTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, null, "  " + TENANT_100_NAME.toUpperCase() + "  "),
                null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: people type their own company name casually, so matching must tolerate case and padding");
        assertEquals(USER_TENANT_ID, readObject(response, LoginResponseDto.class).getProfile().getTenantId());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with a company name belonging to another organization returns 401")
    void loginByWrongCompanyNameTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, null, "Globex"), null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Reason: the user belongs to Acme Corp, so logging in against Globex must fail");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with an unknown company name returns 401 and reveals nothing")
    void loginByUnknownCompanyNameTest() {
        ResponseEntity<String> unknownCompany = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, null, "No Such Company"), null, null);
        ResponseEntity<String> wrongPassword = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, "wrong-password", null, TENANT_100_NAME), null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, unknownCompany.getStatusCode());
        assertEquals(wrongPassword.getStatusCode(), unknownCompany.getStatusCode(),
                "Reason: an unknown organization must be indistinguishable from a wrong password, "
                        + "or login becomes a way to discover which organizations exist");
        assertEquals(wrongPassword.getBody(), unknownCompany.getBody(),
                "Reason: the two rejections must not differ in body either");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with neither an organization id nor a company name returns 401")
    void loginWithoutOrganizationTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, null, null), null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Reason: without an organization there is nothing to authenticate the user against");
    }
}
