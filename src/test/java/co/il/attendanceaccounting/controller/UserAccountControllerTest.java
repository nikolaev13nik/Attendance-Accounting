package co.il.attendanceaccounting.controller;

import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import co.il.attendanceaccounting.dto.UserEditDto;
import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.dto.LoginResponseDto;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;

import static co.il.attendance.security.SecurityConstants.PLATFORM_TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountControllerTest extends BaseApiControllerTest {

    private static final String ACCOUNT_URL = "/account";
    private static final String LOGIN_URL = ACCOUNT_URL + "/login";
    private static final String TENANT_URL = ACCOUNT_URL + "/tenant/";
    private static final String ROLE_URL = "/role/";
    private static final Integer NEW_TENANT_ID = 300;
    private static final String PROTECTED_MESSAGE = "user is protected and cannot be modified";
    private static final String LAST_ADMIN_MESSAGE = "tenant must keep at least one administrator";
    private static final String USER_ROLE = "USER";
    private static final String ADMINISTRATOR_ROLE = "ADMINISTRATOR";
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";
    private static final String NEW_USER_PWD = "pw";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user as the first user of an empty tenant registers them as administrator")
    void registerNewUserTest() {
        ResponseEntity<String> response = registerAsPlatformAdmin(NEW_TENANT_ID,
                createUserRegisterDto(99, NEW_USER_PWD, "New", "User", "new.user@example.com"));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(99, body.getIdUser().intValue());
        assertEquals("New", body.getFirstName());
        assertEquals("User", body.getLastName());
        assertEquals("new.user@example.com", body.getEmail());
        assertEquals(NEW_TENANT_ID, body.getTenantId());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), body.getRoles(),
                "Reason: the first user of a tenant keeps the default 'USER' role and also gets 'ADMINISTRATOR'");
        assertTrue(existsUser(99));
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), findUser(99).orElseThrow().getRoles(),
                "Reason: both roles must be persisted in user_roles, not only returned");
        assertEquals("new.user@example.com", findUser(99).orElseThrow().getEmail());
        assertEquals(NEW_TENANT_ID, findUser(99).orElseThrow().getTenantId());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user for the second user of a tenant grants only the default 'USER' role")
    void registerSecondUserInTenantTest() {
        ResponseEntity<String> first = registerAsPlatformAdmin(NEW_TENANT_ID,
                createUserRegisterDto(99, NEW_USER_PWD, "First", "User", "first.user@example.com"));
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertTrue(readObject(first, UserProfileDto.class).getRoles().contains(ADMINISTRATOR_ROLE),
                "Reason: the first registration must be the administrator for this test to be meaningful");

        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, registerUrl(NEW_TENANT_ID),
                createUserRegisterDto(92, NEW_USER_PWD, "Second", "User", "second.user@example.com"),
                obtainToken(99, NEW_USER_PWD, NEW_TENANT_ID));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(Set.of(USER_ROLE), body.getRoles(),
                "Reason: only the first user of a tenant is promoted, every later one stays a plain user");
        assertEquals(Set.of(USER_ROLE), findUser(92).orElseThrow().getRoles());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), findUser(99).orElseThrow().getRoles(),
                "Reason: registering a second user must not strip the first user's administrator role");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user as the first user of a tenant can then use an administrator-only endpoint")
    void firstUserOfTenantIsAuthorizedAsAdminTest() {
        ResponseEntity<String> admin = registerAsPlatformAdmin(NEW_TENANT_ID,
                createUserRegisterDto(91, NEW_USER_PWD, "Tenant", "Admin", "tenant.admin@example.com"));
        assertEquals(HttpStatus.CREATED, admin.getStatusCode());
        String token = obtainToken(91, NEW_USER_PWD, NEW_TENANT_ID);
        ResponseEntity<String> member = sendWithToken(HttpMethod.POST, registerUrl(NEW_TENANT_ID),
                createUserRegisterDto(90, NEW_USER_PWD, "Tenant", "Member", "tenant.member@example.com"), token);
        assertEquals(HttpStatus.CREATED, member.getStatusCode());

        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, roleUrl(NEW_TENANT_ID, 90, "Administrator"),
                null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: the auto-granted role must survive login into the JWT authorities claim and satisfy hasRole('ADMINISTRATOR')");
        assertTrue(findUser(90).orElseThrow().getRoles().contains(ADMINISTRATOR_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user into an emptied tenant: 401 anonymously, administrator when the platform admin adds it")
    void registerIntoEmptiedTenantTest() throws Exception {
        ResponseEntity<String> deleted = sendWithToken(HttpMethod.DELETE,
                userUrl(OTHER_USER_TENANT_ID, OTHER_USER_ID), null, platformAdminToken());
        assertEquals(HttpStatus.OK, deleted.getStatusCode());
        assertFalse(existsUser(OTHER_USER_ID));

        ResponseEntity<String> anonymous = send(HttpMethod.POST, registerUrl(OTHER_USER_TENANT_ID),
                createUserRegisterDto(83, NEW_USER_PWD, "Claim", "Attempt", "claim@example.com"), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode(),
                "Reason: an emptied tenant must not be claimable anonymously, only an admin or the platform admin may add users");
        assertFalse(existsUser(83));

        ResponseEntity<String> response = registerAsPlatformAdmin(OTHER_USER_TENANT_ID,
                createUserRegisterDto(89, NEW_USER_PWD, "New", "Owner", "new.owner@example.com"));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), readObject(response, UserProfileDto.class).getRoles(),
                "Reason: the rule is 'no users in the tenant', so a tenant left empty gets an administrator again");
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), findUser(89).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user with an id already used in the same tenant returns 409 CONFLICT")
    void registerExistingUserTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, registerUrl(USER_TENANT_ID),
                createUserRegisterDto(USER_ID, "pw", "Dup", "User", "dup@example.com"), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("user exists", errorMessage(response));
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user with an id used in another tenant creates a separate user there")
    void registerSameIdInAnotherTenantTest() {
        ResponseEntity<String> response = registerAsPlatformAdmin(OTHER_USER_TENANT_ID,
                createUserRegisterDto(USER_ID, NEW_USER_PWD, "Johnny", "Elsewhere", "johnny@globex.local"));
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Reason: user ids are unique per company, so the same person can belong to two companies");

        User inAcme = userRepository.findById(new UserBasicKey(USER_TENANT_ID, USER_ID)).orElseThrow();
        User inGlobex = userRepository.findById(new UserBasicKey(OTHER_USER_TENANT_ID, USER_ID)).orElseThrow();
        assertEquals("John", inAcme.getFirstName(), "Reason: the existing user in the first company is untouched");
        assertEquals("Johnny", inGlobex.getFirstName());

        ResponseEntity<String> acmeLogin = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, USER_TENANT_ID, null), null, null);
        ResponseEntity<String> globexLogin = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, NEW_USER_PWD, OTHER_USER_TENANT_ID, null), null, null);
        assertEquals(USER_TENANT_ID, readObject(acmeLogin, LoginResponseDto.class).getProfile().getTenantId());
        assertEquals(OTHER_USER_TENANT_ID, readObject(globexLogin, LoginResponseDto.class).getProfile().getTenantId(),
                "Reason: each login resolves the user of the company it names");
        ResponseEntity<String> crossed = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, USER_PWD, OTHER_USER_TENANT_ID, null), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, crossed.getStatusCode(),
                "Reason: one company's password must not open the same id in another company");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user with a malformed email returns 400 BAD_REQUEST")
    void registerWithInvalidEmailTest() {
        String token = platformAdminToken();
        long before = userRepository.count();
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, registerUrl(NEW_TENANT_ID), createUserRegisterDto(98, "pw", "New", "User", "not-an-email"), token);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
        assertFalse(existsUser(98));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user without an email returns 400 BAD_REQUEST")
    void registerWithMissingEmailTest() {
        String token = platformAdminToken();
        long before = userRepository.count();
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, registerUrl(NEW_TENANT_ID), createUserRegisterDto(97, "pw", "New", "User", null), token);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
        assertFalse(existsUser(97));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user with a non-numeric tenantId returns 400 BAD_REQUEST")
    void registerWithMalformedTenantIdTest() {
        String token = platformAdminToken();
        long before = userRepository.count();
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, TENANT_URL + "abc/user",
                createUserRegisterDto(96, "pw", "New", "User", "new.user2@example.com"), token);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
        assertFalse(existsUser(96));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user with a tenantId missing from att_ac_tenant_mapping returns 400 BAD_REQUEST")
    void registerWithUnknownTenantIdTest() {
        String token = platformAdminToken();
        long before = userRepository.count();
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, registerUrl(UNKNOWN_TENANT_ID), createUserRegisterDto(95, "pw", "New", "User", "new.user3@example.com"), token);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Tenant with id = " + UNKNOWN_TENANT_ID + " not found", errorMessage(response));
        assertEquals(before, userRepository.count(), "Reason: no user should be added for an unknown tenant");
        assertFalse(existsUser(95));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user by that tenant's admin registers the user as a plain user")
    void registerWithSeededTenantIdTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, registerUrl(TENANT_ID_100),
                createUserRegisterDto(94, NEW_USER_PWD, "New", "User", "new.user4@example.com"), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(TENANT_ID_100, body.getTenantId());
        assertEquals(TENANT_ID_100, findUser(94).orElseThrow().getTenantId());
        assertEquals(Set.of(USER_ROLE), body.getRoles(),
                "Reason: tenant 100 already holds seeded users, so the newcomer must not be promoted");
        assertFalse(findUser(94).orElseThrow().getRoles().contains(ADMINISTRATOR_ROLE));
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
        assertTrue(profile.getRoles().contains(USER_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login for a user whose tenant has no mapping row returns a profile without companyName")
    void loginUnmappedTenantTest() {
        registerAsPlatformAdmin(NEW_TENANT_ID,
                createUserRegisterDto(98, NEW_USER_PWD, "No", "Tenant", "no.tenant@example.com"));
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
    @DisplayName("POST /account/login without credentials returns 400 BAD_REQUEST")
    void loginAnonymousTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, createLoginRequestDto(null, null, null, null),
                null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Reason: idUser and password are required by the contract");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login without a password returns 400 BAD_REQUEST")
    void loginMissingPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL,
                createLoginRequestDto(USER_ID, null, USER_TENANT_ID, null), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Reason: password is required by the contract");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login without a body returns 400 BAD_REQUEST")
    void loginWithoutBodyTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, null, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Reason: the login body is required by the contract");
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
        assertFalse(existsUser(USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} as non-admin returns 403 and keeps the user")
    void deleteUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(USER_TENANT_ID, OTHER_USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(existsUser(OTHER_USER_ID));
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
    @DisplayName("DELETE /account/tenant/0/user/{platformAdmin} returns 403 even for the platform admin and keeps the account")
    void deleteProtectedAdminTest() {
        ResponseEntity<String> response = sendWithToken(HttpMethod.DELETE, userUrl(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID),
                null, platformAdminToken());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(PROTECTED_MESSAGE, errorMessage(response));
        assertTrue(userRepository.existsById(new UserBasicKey(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID)));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} removes an ordinary user who happens to share the platform admin's id")
    void deleteUserSharingProtectedIdTest() {
        userRepository.save(User.builder().userBasicKey(new UserBasicKey(USER_TENANT_ID, PROTECTED_ADMIN_ID)).password("irrelevant")
                .role(USER_ROLE).build());

        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(USER_TENANT_ID, PROTECTED_ADMIN_ID), null, ADMIN_ID,
                ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: only the bootstrap account in the platform tenant is protected, not the id number everywhere");
        assertFalse(userRepository.existsById(new UserBasicKey(USER_TENANT_ID, PROTECTED_ADMIN_ID)));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} still removes an ordinary user when a protected id exists")
    void deleteOrdinaryUserStillWorksTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(ADMIN_TENANT_ID, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(existsUser(USER_ID),
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
        assertEquals("Johnny", findUser(USER_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} as the same user with the right current password changes it")
    void editOwnPasswordWithCurrentPasswordTest() {
        UserEditDto edit = createUserEditDto(null, null, "user-new-pwd", null);
        edit.setCurrentPassword(USER_PWD);

        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID), edit, USER_ID, USER_PWD);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(passwordEncoder.matches("user-new-pwd", findUser(USER_ID).orElseThrow().getPassword()),
                "Reason: the user proved who they are, so the new password is stored");
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} as the same user without the current password returns 400")
    void editOwnPasswordWithoutCurrentPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID),
                createUserEditDto(null, null, "stolen-token-pwd", null), USER_ID, USER_PWD);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Reason: a valid token alone must not be enough to take over the account");
        assertEquals("Current password is missing or incorrect", errorMessage(response));
        assertTrue(passwordEncoder.matches(USER_PWD, findUser(USER_ID).orElseThrow().getPassword()),
                "Reason: the rejected request must leave the old password in place");
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} as the same user with a wrong current password returns 400")
    void editOwnPasswordWithWrongCurrentPasswordTest() {
        UserEditDto edit = createUserEditDto("Johnny", null, "user-new-pwd", null);
        edit.setCurrentPassword("not-my-password");

        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID), edit, USER_ID, USER_PWD);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Current password is missing or incorrect", errorMessage(response));
        User user = findUser(USER_ID).orElseThrow();
        assertTrue(passwordEncoder.matches(USER_PWD, user.getPassword()), "Reason: the password is unchanged");
        assertEquals("John", user.getFirstName(), "Reason: a rejected request changes nothing, not even the name");
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} by an admin for another user resets it without the current password")
    void adminResetsAnotherUsersPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID),
                createUserEditDto(null, null, "reset-by-admin", null), ADMIN_ID, ADMIN_PWD);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: an administrator resets a forgotten password and cannot know the old one");
        assertTrue(passwordEncoder.matches("reset-by-admin", findUser(USER_ID).orElseThrow().getPassword()));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} by an admin for their own account still needs the current password")
    void adminOwnPasswordNeedsCurrentPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(ADMIN_TENANT_ID, ADMIN_ID),
                createUserEditDto(null, null, "admin-new-pwd", null), ADMIN_ID, ADMIN_PWD);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Reason: the administrator role does not exempt a caller from proving it is them");
        assertTrue(passwordEncoder.matches(ADMIN_PWD, findUser(ADMIN_ID).orElseThrow().getPassword()));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} for another user (non-admin) returns 403")
    void editUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, OTHER_USER_ID), createUserEditDto("Hacker", null, null, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertEquals("Jane", findUser(OTHER_USER_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} as the same user updates the email")
    void editUserEmailTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID), createUserEditDto(null, null, null, "john.new@example.com"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals("john.new@example.com", body.getEmail());
        assertEquals("john.new@example.com", findUser(USER_ID).orElseThrow().getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{tenantId}/user/password/{id} with a malformed email returns 400 and keeps the original email")
    void editUserInvalidEmailTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(USER_TENANT_ID, USER_ID), createUserEditDto(null, null, null, "not-an-email"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(USER_EMAIL, findUser(USER_ID).orElseThrow().getEmail());
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
        assertFalse(existsUser(987654));
        assertEquals(usersBefore, userRepository.count());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user/{id}/role/{role} as admin adds the role")
    void addRoleAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(ADMIN_TENANT_ID, USER_ID, "Administrator"), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertTrue(body.getRoles().contains(ADMINISTRATOR_ROLE));
        assertTrue(body.getRoles().contains(USER_ROLE));
        assertTrue(findUser(USER_ID).orElseThrow().getRoles().contains(ADMINISTRATOR_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user/{id}/role/{role} as non-admin returns 403")
    void addRoleForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(USER_TENANT_ID, USER_ID, "Administrator"), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertFalse(findUser(USER_ID).orElseThrow().getRoles().contains(ADMINISTRATOR_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id}/role/{role} as admin removes the role")
    void removeRoleAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, USER_ID, "User"), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertFalse(body.getRoles().contains(USER_ROLE));
        assertFalse(findUser(USER_ID).orElseThrow().getRoles().contains(USER_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id}/role/{role} as non-admin returns 403")
    void removeRoleForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(USER_TENANT_ID, USER_ID, "User"), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(findUser(USER_ID).orElseThrow().getRoles().contains(USER_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("Bootstrap creates the platform admin in tenant 0, and its token passes administrator-only endpoints")
    void bootstrapPlatformAdminPassesAdministratorEndpointTest() throws Exception {
        application.run();
        User platformAdmin = findUser(PROTECTED_ADMIN_ID).orElseThrow();
        assertEquals(Set.of(PLATFORM_ADMIN_ROLE), platformAdmin.getRoles(),
                "Reason: the platform admin holds only PLATFORM_ADMIN, the hierarchy implies the rest");
        assertEquals(PLATFORM_TENANT_ID, platformAdmin.getTenantId());

        String token = obtainToken(PROTECTED_ADMIN_ID, BOOTSTRAP_PWD, PLATFORM_TENANT_ID);
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST,
                roleUrl(USER_TENANT_ID, USER_ID, "Administrator"), null, token);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: PLATFORM_ADMIN must imply ADMINISTRATOR through the role hierarchy");
        assertTrue(findUser(USER_ID).orElseThrow().getRoles().contains(ADMINISTRATOR_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("Bootstrap resets the platform admin's roles and leaves a same-id user of another tenant alone")
    void bootstrapResetsPlatformAdminRolesAndTenantTest() throws Exception {
        userRepository.save(User.builder().userBasicKey(new UserBasicKey(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID)).password("irrelevant")
                .role("User").role("Administrator").build());
        userRepository.save(User.builder().userBasicKey(new UserBasicKey(USER_TENANT_ID, PROTECTED_ADMIN_ID)).password("irrelevant")
                .role(USER_ROLE).build());

        application.run();

        User platformAdmin = userRepository.findById(new UserBasicKey(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID)).orElseThrow();
        assertEquals(Set.of(PLATFORM_ADMIN_ROLE), platformAdmin.getRoles(),
                "Reason: startup must restore the platform admin's role set, whatever was stored before");
        assertEquals(Set.of(USER_ROLE),
                userRepository.findById(new UserBasicKey(USER_TENANT_ID, PROTECTED_ADMIN_ID)).orElseThrow().getRoles(),
                "Reason: the bootstrap owns only the platform tenant's account, not every user with that id");
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
        assertTrue(body.getRoles().contains(USER_ROLE));
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
        assertTrue(existsUser(OTHER_USER_ID));
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
        assertTrue(existsUser(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/{own}/user/password/{id} by an admin for a user of another tenant returns 404 and changes nothing")
    void editUserOfOtherTenantAsAdminTest() {
        String passwordBefore = findUser(OTHER_USER_ID).orElseThrow().getPassword();
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(ADMIN_TENANT_ID, OTHER_USER_ID),
                createUserEditDto("Hacked", null, "new-password", null), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        User other = findUser(OTHER_USER_ID).orElseThrow();
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
        assertEquals(Set.of(USER_ROLE), findUser(OTHER_USER_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{own}/user/{id}/role/{role} by an admin for a user of another tenant returns 404 and keeps the roles")
    void removeRoleOfOtherTenantAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, OTHER_USER_ID, "User"),
                null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(Set.of(USER_ROLE), findUser(OTHER_USER_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/200/user/{id} as the platform admin removes that tenant's user")
    void deleteUserAsPlatformAdminTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.DELETE,
                userUrl(OTHER_USER_TENANT_ID, OTHER_USER_ID), null, platformAdminToken());
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: the platform admin may act on any tenant it names");
        assertFalse(existsUser(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{t}/user/{id} as the platform admin naming the wrong tenant returns 404 and keeps the user")
    void deleteUserAsPlatformAdminWrongTenantTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.DELETE,
                userUrl(USER_TENANT_ID, OTHER_USER_ID), null, platformAdminToken());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Reason: the user is looked up inside the named tenant only");
        assertTrue(existsUser(OTHER_USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /account/tenant/0/user/password/{id} lets the platform admin edit its own profile")
    void editSelfAsPlatformAdminTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.PUT,
                passwordUrl(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID),
                createUserEditDto("Support", null, null, null), platformAdminToken());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Support", findUser(PROTECTED_ADMIN_ID).orElseThrow().getFirstName());
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
        ResponseEntity<String> response = send(HttpMethod.PUT, passwordUrl(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID),
                createUserEditDto("Owned", null, "attacker-password", null), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: tenant 0 is the system tenant, only the platform admin's own JWT carries it");
        assertEquals("Platform", findUser(PROTECTED_ADMIN_ID).orElseThrow().getFirstName());
        assertFalse(obtainToken(PROTECTED_ADMIN_ID, BOOTSTRAP_PWD, PLATFORM_TENANT_ID).isBlank(),
                "Reason: the platform admin's password must be unchanged");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user/{id}/role/{role} stores the role uppercase whatever case is sent")
    void addRoleNormalizesCaseTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(ADMIN_TENANT_ID, USER_ID, " administrator "),
                null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), readObject(response, UserProfileDto.class).getRoles());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), findUser(USER_ID).orElseThrow().getRoles(),
                "Reason: roles are stored in one canonical case, so removal and comparison never miss");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user/{id}/role/{role} with an unknown role returns 400 and stores nothing")
    void addUnknownRoleTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, roleUrl(ADMIN_TENANT_ID, USER_ID, "Banana"), null,
                ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Role Banana is not allowed", errorMessage(response));
        assertEquals(Set.of(USER_ROLE), findUser(USER_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user/{id}/role/PLATFORM_ADMIN returns 400 even for the platform admin")
    void addPlatformAdminRoleTest() throws Exception {
        String token = platformAdminToken();
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST,
                roleUrl(USER_TENANT_ID, USER_ID, "platform_admin"), null, token);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Reason: PLATFORM_ADMIN belongs to the bootstrap account only and is never granted through the API");
        assertEquals(Set.of(USER_ROLE), findUser(USER_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/0/user/{platformAdmin}/role/PLATFORM_ADMIN returns 400 and keeps the role")
    void removePlatformAdminRoleTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.DELETE,
                roleUrl(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID, PLATFORM_ADMIN_ROLE), null, platformAdminToken());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Set.of(PLATFORM_ADMIN_ROLE), findUser(PROTECTED_ADMIN_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/0/user/{platformAdmin}/role/{role} returns 403 because the account is protected")
    void changeRolesOfProtectedAccountTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST,
                roleUrl(PLATFORM_TENANT_ID, PROTECTED_ADMIN_ID, ADMINISTRATOR_ROLE), null, platformAdminToken());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(PROTECTED_MESSAGE, errorMessage(response));
        assertEquals(Set.of(PLATFORM_ADMIN_ROLE), findUser(PROTECTED_ADMIN_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id}/role/ADMINISTRATOR removes a role stored in legacy mixed case")
    void removeLegacyMixedCaseAdministratorTest() {
        userRepository.save(User.builder().userBasicKey(new UserBasicKey(ADMIN_TENANT_ID, 50)).password(passwordEncoder.encode(NEW_USER_PWD))
                .role("User").role("Administrator").build());

        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, 50, ADMINISTRATOR_ROLE), null,
                ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Set.of("User"), findUser(50).orElseThrow().getRoles(),
                "Reason: revoking ADMINISTRATOR must also remove a row stored as 'Administrator'");

        String token = obtainToken(50, NEW_USER_PWD, ADMIN_TENANT_ID);
        ResponseEntity<String> adminCall = sendWithToken(HttpMethod.DELETE, userUrl(ADMIN_TENANT_ID, USER_ID), null, token);
        assertEquals(HttpStatus.FORBIDDEN, adminCall.getStatusCode(),
                "Reason: after revocation the next token must no longer carry ADMINISTRATOR");
        assertTrue(existsUser(USER_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id}/role/ADMINISTRATOR on the tenant's last admin returns 409")
    void removeLastAdministratorRoleTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, ADMIN_ID, ADMINISTRATOR_ROLE),
                null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
                "Reason: a tenant must never be left without an administrator");
        assertEquals(LAST_ADMIN_MESSAGE, errorMessage(response));
        assertTrue(findUser(ADMIN_ID).orElseThrow().getRoles().contains(ADMINISTRATOR_ROLE));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id} on the tenant's last admin returns 409 and keeps the user")
    void deleteLastAdministratorTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, userUrl(ADMIN_TENANT_ID, ADMIN_ID), null, ADMIN_ID,
                ADMIN_PWD);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(LAST_ADMIN_MESSAGE, errorMessage(response));
        assertTrue(existsUser(ADMIN_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /account/tenant/{tenantId}/user/{id}/role/ADMINISTRATOR works once another admin exists")
    void removeAdministratorWhenAnotherExistsTest() {
        ResponseEntity<String> granted = send(HttpMethod.POST, roleUrl(ADMIN_TENANT_ID, USER_ID, ADMINISTRATOR_ROLE),
                null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, granted.getStatusCode());

        ResponseEntity<String> response = send(HttpMethod.DELETE, roleUrl(ADMIN_TENANT_ID, ADMIN_ID, ADMINISTRATOR_ROLE),
                null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Reason: the guard only protects the last administrator, not every administrator");
        assertEquals(Set.of(USER_ROLE), findUser(ADMIN_ID).orElseThrow().getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user anonymously into a tenant that has users returns 401 and adds nobody")
    void registerAnonymouslyIntoPopulatedTenantTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, registerUrl(TENANT_ID_100),
                createUserRegisterDto(88, NEW_USER_PWD, "Stranger", "Danger", "stranger@example.com"), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Reason: only the first user of an empty tenant may self-register, anyone else must be added by an admin");
        assertEquals(before, userRepository.count());
        assertFalse(existsUser(88));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user anonymously with a taken id into a populated tenant returns 401, not 409")
    void registerAnonymouslyWithTakenIdDoesNotRevealItTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, registerUrl(TENANT_ID_100),
                createUserRegisterDto(OTHER_USER_ID, NEW_USER_PWD, "Probe", "Probe", "probe@example.com"), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Reason: the caller check runs first, so a stranger cannot learn which user ids exist");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user by a plain user of that tenant returns 403")
    void registerAsPlainUserTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, registerUrl(USER_TENANT_ID),
                createUserRegisterDto(87, NEW_USER_PWD, "New", "Colleague", "colleague@example.com"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertFalse(existsUser(87));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user by an admin of another tenant returns 403")
    void registerAsAdminOfOtherTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, registerUrl(OTHER_USER_TENANT_ID),
                createUserRegisterDto(86, NEW_USER_PWD, "Planted", "User", "planted@example.com"), ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: an admin adds users to its own company only");
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertFalse(existsUser(86));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user by the platform admin adds a plain user to a populated tenant")
    void registerAsPlatformAdminIntoPopulatedTenantTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, registerUrl(OTHER_USER_TENANT_ID),
                createUserRegisterDto(85, NEW_USER_PWD, "Support", "Added", "support.added@example.com"),
                platformAdminToken());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        User created = findUser(85).orElseThrow();
        assertEquals(OTHER_USER_TENANT_ID, created.getTenantId());
        assertEquals(Set.of(USER_ROLE), created.getRoles());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user by the platform admin into an empty tenant creates its administrator")
    void registerAsPlatformAdminIntoEmptyTenantTest() throws Exception {
        ResponseEntity<String> response = sendWithToken(HttpMethod.POST, registerUrl(NEW_TENANT_ID),
                createUserRegisterDto(84, NEW_USER_PWD, "First", "Owner", "first.owner@example.com"),
                platformAdminToken());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(Set.of(USER_ROLE, ADMINISTRATOR_ROLE), findUser(84).orElseThrow().getRoles(),
                "Reason: the platform admin can set up a company's first administrator on its behalf");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/{tenantId}/user anonymously into an empty tenant returns 401 - only signup creates a first admin")
    void registerAnonymouslyIntoEmptyTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, registerUrl(NEW_TENANT_ID),
                createUserRegisterDto(82, NEW_USER_PWD, "Early", "Bird", "early.bird@example.com"), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Reason: an empty tenant is claimable only by the platform admin; new organizations use /account/signup");
        assertFalse(existsUser(82));
    }

    private ResponseEntity<String> registerAsPlatformAdmin(Integer tenantId, Object body) {
        return sendWithToken(HttpMethod.POST, registerUrl(tenantId), body, platformAdminToken());
    }

    private String userByTenantUrl(Integer tenantId, Integer idUser) {
        return TENANT_URL + tenantId + "/userId/" + idUser;
    }

    private String registerUrl(Integer tenantId) {
        return TENANT_URL + tenantId + "/user";
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
