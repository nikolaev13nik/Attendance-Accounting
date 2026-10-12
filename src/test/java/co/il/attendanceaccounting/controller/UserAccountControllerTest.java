package co.il.attendanceaccounting.controller;

import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.security.dto.LoginRequestDto;
import co.il.attendanceaccounting.security.dto.LoginResponseDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountControllerTest extends BaseApiControllerTest {

    private static final String ACCOUNT_URL = "/account";
    private static final String USER_URL = ACCOUNT_URL + "/user";
    private static final String USER_PASSWORD_URL = USER_URL + "/password/";
    private static final String LOGIN_URL = ACCOUNT_URL + "/login";
    private static final String USERS_URL = ACCOUNT_URL + "/users";
    private static final String ROLE_URL = "/role/";
    private static final String TENANT_ADD_URL = ACCOUNT_URL + "/tenant/add";
    private static final Integer NEW_TENANT_ID = 300;
    private static final Integer ADDED_TENANT_ID = 401;
    private static final Integer PROTECTED_ADMIN_ID = 123456789;
    private static final String PROTECTED_MESSAGE = "user is protected and cannot be deleted";
    private static final String USER_ROLE = "USER";
    private static final String ADMINISTRATOR_ROLE = "ADMINISTRATOR";
    private static final String NEW_USER_PWD = "pw";

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
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, USER_URL + "/" + 90 + ROLE_URL + "Moderator",
                null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: the auto-granted role must survive login into the JWT authorities claim and satisfy hasRole('ADMINISTRATOR')");
        assertTrue(userRepository.findById(90).orElseThrow().getRoles().contains("Moderator"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user into a tenant whose users were all removed grants administrator again")
    void registerIntoEmptiedTenantTest() {
        ResponseEntity<String> deleted = send(HttpMethod.DELETE, USER_URL + "/" + OTHER_USER_ID, null, ADMIN_ID,
                ADMIN_PWD);
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
                createTenantAddDto(ADDED_TENANT_ID, "Initrode", "admin@initrode.local"), null, null);
        assertEquals(HttpStatus.CREATED, tenantResponse.getStatusCode());

        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(93, NEW_USER_PWD, "New", "User", "new.user5@example.com", ADDED_TENANT_ID), null,
                null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Reason: registration must accept a tenant that was just onboarded through the add-tenant API");
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(ADDED_TENANT_ID, body.getTenantId());
        assertEquals(ADDED_TENANT_ID, userRepository.findById(93).orElseThrow().getTenantId());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), body.getRoles(),
                "Reason: onboarding a tenant and registering into it must yield that tenant's first administrator");
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), userRepository.findById(93).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with valid credentials  returns token and profile")
    void loginValidTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto( USER_ID, USER_PWD, USER_TENANT_ID),null,null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponseDto body = readObject(response, LoginResponseDto.class);
        assertEquals("Bearer", body.getTokenType());
        UserProfileDto profile = body.getProfile();
        assertEquals(USER_ID,profile.getIdUser().intValue());
        assertEquals("John", profile.getFirstName());
        assertEquals("Doe", profile.getLastName());
        assertEquals(USER_EMAIL, profile.getEmail());
        assertEquals(USER_TENANT_ID, profile.getTenantId());
        assertTrue(profile.getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login without credentials returns 401 UNAUTHORIZED")
    void loginAnonymousTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto(null,null, null), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with a wrong password returns 401 UNAUTHORIZED")
    void loginWrongPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto(USER_ID,"wrong-password", USER_TENANT_ID),null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with a wrong tenantId returns 401 UNAUTHORIZED")
    void loginWrongTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto(USER_ID, USER_PWD, OTHER_USER_TENANT_ID), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login without a tenantId returns 401 UNAUTHORIZED")
    void loginMissingTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto(USER_ID, USER_PWD, null), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id} as admin removes the user")
    void deleteUserAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/" + OTHER_USER_ID, null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(OTHER_USER_ID, body.getIdUser().intValue());
        assertEquals("Jane", body.getFirstName());
        assertEquals("Roe", body.getLastName());
        assertEquals(OTHER_USER_EMAIL, body.getEmail());
        assertFalse(userRepository.existsById(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id} as non-admin returns 403 and keeps the user")
    void deleteUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/" + OTHER_USER_ID, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(userRepository.existsById(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id} for a missing user (admin) returns 400")
    void deleteUserMissingTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/987654", null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("User with login = 987654 not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id} for the bootstrap super-admin returns 403 even as admin, and 403 not 400 when absent")
    void deleteProtectedAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/" + PROTECTED_ADMIN_ID, null, ADMIN_ID,
                ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: the protected id is rejected before the existence lookup, so the response never reveals whether the super-admin exists");
        assertEquals(PROTECTED_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id} for the bootstrap super-admin keeps the existing row")
    void deleteProtectedAdminKeepsRowTest() {
        ResponseEntity<String> created = send(HttpMethod.POST, USER_URL,
                createUserRegisterDto(PROTECTED_ADMIN_ID, "pw", "Super", "Admin", "super.admin@example.com",
                        NEW_TENANT_ID), null, null);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/" + PROTECTED_ADMIN_ID, null, ADMIN_ID,
                ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(PROTECTED_MESSAGE, errorMessage(response));
        assertTrue(userRepository.existsById(PROTECTED_ADMIN_ID),
                "Reason: the protected user must survive a delete attempt, so the startup bootstrap never recreates it");
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id} still removes an ordinary user when a protected id exists")
    void deleteOrdinaryUserStillWorksTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/" + USER_ID, null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(userRepository.existsById(USER_ID),
                "Reason: protection must apply to the configured id only, not to deletion in general");
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/user/password/{id} as the same user updates the profile")
    void editUserSelfTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, USER_PASSWORD_URL + USER_ID, createUserEditDto("Johnny", null, null, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals("Johnny", body.getFirstName());
        assertEquals("Johnny", userRepository.findById(USER_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT/account/user/password/{id} for another user (non-admin) returns 403")
    void editUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, USER_PASSWORD_URL + OTHER_USER_ID, createUserEditDto("Hacker", null, null, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertEquals("Jane", userRepository.findById(OTHER_USER_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/user/password/{id} as the same user updates the email")
    void editUserEmailTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, USER_PASSWORD_URL + USER_ID, createUserEditDto(null, null, null, "john.new@example.com"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals("john.new@example.com", body.getEmail());
        assertEquals("john.new@example.com", userRepository.findById(USER_ID).orElseThrow().getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/user/password/{id} with a malformed email returns 400 and keeps the original email")
    void editUserInvalidEmailTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, USER_PASSWORD_URL + USER_ID, createUserEditDto(null, null, null, "not-an-email"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(USER_EMAIL, userRepository.findById(USER_ID).orElseThrow().getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user/{id}/role/{role} as admin adds the role")
    void addRoleAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL + "/" + USER_ID + ROLE_URL + "Moderator", null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertTrue(body.getRoles().contains("Moderator"));
        assertTrue(body.getRoles().contains("User"));
        assertTrue(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("Moderator"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user/{id}/role/{role} as non-admin returns 403")
    void addRoleForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL + "/" + USER_ID + ROLE_URL + "Moderator", null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertFalse(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("Moderator"));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id}/role/{role} as admin removes the role")
    void removeRoleAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/" + USER_ID + ROLE_URL + "User", null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertFalse(body.getRoles().contains("User"));
        assertFalse(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/user/{id}/role/{role} as non-admin returns 403")
    void removeRoleForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, USER_URL + "/" + USER_ID + ROLE_URL + "User", null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(userRepository.findById(USER_ID).orElseThrow().getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/users returns the seeded users")
    void getAllUsersTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<UserProfileDto> users = readList(response, UserProfileDto.class);
        List<Integer> ids = users.stream().map(UserProfileDto::getIdUser).toList();
        assertEquals((int) userRepository.count(), users.size());
        assertTrue(ids.containsAll(List.of(ADMIN_ID, USER_ID, OTHER_USER_ID)));
        assertEquals(USER_EMAIL, users.stream().filter(u -> u.getIdUser().equals(USER_ID)).findFirst().orElseThrow().getEmail());
        assertEquals(ADMIN_EMAIL, users.stream().filter(u -> u.getIdUser().equals(ADMIN_ID)).findFirst().orElseThrow().getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/users never exposes the password field")
    void getAllUsersNoPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        json(response).forEach(node -> assertFalse(node.has("password"), "Reason: profile must not leak the password"));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/users without a token returns 401 UNAUTHORIZED")
    void getAllUsersUnauthorizedTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL, null, null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/users?tenantId=100 as a non-admin returns only that tenant's users")
    void getAllUsersByTenantIdTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL + "?tenantId=" + USER_TENANT_ID, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<UserProfileDto> users = readList(response, UserProfileDto.class);
        List<Integer> ids = users.stream().map(UserProfileDto::getIdUser).toList();
        assertEquals(2, users.size());
        assertTrue(ids.containsAll(List.of(ADMIN_ID, USER_ID)));
        assertFalse(ids.contains(OTHER_USER_ID));
        assertTrue(users.stream().allMatch(u -> USER_TENANT_ID.equals(u.getTenantId())));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/users?tenantId=200 returns only that tenant's users")
    void getAllUsersByOtherTenantIdTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL + "?tenantId=" + OTHER_USER_TENANT_ID, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<UserProfileDto> users = readList(response, UserProfileDto.class);
        assertEquals(1, users.size());
        assertEquals(OTHER_USER_ID, users.get(0).getIdUser());
        assertEquals(OTHER_USER_TENANT_ID, users.get(0).getTenantId());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/users?tenantId=999 returns an empty list")
    void getAllUsersUnknownTenantIdTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL + "?tenantId=999", null, USER_ID, USER_PWD);
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
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} with a mismatched tenant returns 400")
    void getUserByTenantAndIdWrongTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(OTHER_USER_TENANT_ID, USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("User with login = " + USER_ID + " not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} for a missing user returns 400")
    void getUserByTenantAndIdMissingTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(USER_TENANT_ID, 987654), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("User with login = 987654 not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenant/{tenantId}/userId/{idUser} without a token returns 401 UNAUTHORIZED")
    void getUserByTenantAndIdUnauthorizedTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, userByTenantUrl(USER_TENANT_ID, USER_ID), null, null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private String userByTenantUrl(Integer tenantId, Integer idUser) {
        return ACCOUNT_URL + "/tenant/" + tenantId + "/userId/" + idUser;
    }
}