package co.il.avivsmile.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import co.il.avivsmile.security.dto.LoginRequestDto;
import co.il.avivsmile.security.dto.LoginResponseDto;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import co.il.avivsmile.dto.UserProfileDto;

class UserAccountControllerTest extends BaseApiControllerTest {

    private static final String ACCOUNT_URL = "/account";
    private static final String USER_URL = ACCOUNT_URL + "/user";
    private static final String USER_PASSWORD_URL = USER_URL + "/password/";
    private static final String LOGIN_URL = ACCOUNT_URL + "/login";
    private static final String USERS_URL = ACCOUNT_URL + "/users";
    private static final String ROLE_URL = "/role/";

    @Test
    @FlywayTest
    @DisplayName("POST /account/user registers a new user (public) and persists it")
    void registerNewUserTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL, createUserRegisterDto(99, "pw", "New", "User"), null, null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals(99, body.getIdUser().intValue());
        assertEquals("New", body.getFirstName());
        assertEquals("User", body.getLastName());
        assertTrue(body.getRoles().contains("User"), "Reason: new user gets the default 'User' role");
        assertEquals(1, body.getRoles().size());
        assertTrue(userRepository.existsById(99));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/user with an existing id returns 409 CONFLICT")
    void registerExistingUserTest() {
        long before = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, USER_URL, createUserRegisterDto(USER_ID, "pw", "Dup", "User"), null, null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("user exists", errorMessage(response));
        assertEquals(before, userRepository.count(), "Reason: no user should be added");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with valid credentials  returns token and profile")
    void loginValidTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto( USER_ID, USER_PWD),null,null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponseDto body = readObject(response, LoginResponseDto.class);
        assertEquals("Bearer", body.getTokenType());
        UserProfileDto profile = body.getProfile();
        assertEquals(USER_ID,profile.getIdUser().intValue());
        assertEquals("John", profile.getFirstName());
        assertEquals("Doe", profile.getLastName());
        assertTrue(profile.getRoles().contains("User"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login without credentials returns 401 UNAUTHORIZED")
    void loginAnonymousTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto(null,null), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/login with a wrong password returns 401 UNAUTHORIZED")
    void loginWrongPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, LOGIN_URL, new LoginRequestDto(USER_ID,"wrong-password"),null, null);
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
    @DisplayName("PUT /account/user/password/{id} as the same user updates the profile")
    void editUserSelfTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, USER_PASSWORD_URL + USER_ID, createUserEditDto("Johnny", null, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileDto body = readObject(response, UserProfileDto.class);
        assertEquals("Johnny", body.getFirstName());
        assertEquals("Johnny", userRepository.findById(USER_ID).orElseThrow().getFirstName());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT/account/user/password/{id} for another user (non-admin) returns 403")
    void editUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, USER_PASSWORD_URL + OTHER_USER_ID, createUserEditDto("Hacker", null, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertEquals("Jane", userRepository.findById(OTHER_USER_ID).orElseThrow().getFirstName());
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
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL, null, null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<UserProfileDto> users = readList(response, UserProfileDto.class);
        List<Integer> ids = users.stream().map(UserProfileDto::getIdUser).toList();
        assertEquals((int) userRepository.count(), users.size());
        assertTrue(ids.containsAll(List.of(ADMIN_ID, USER_ID, OTHER_USER_ID)));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/users never exposes the password field")
    void getAllUsersNoPasswordTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, USERS_URL, null, null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        json(response).forEach(node -> assertFalse(node.has("password"), "Reason: profile must not leak the password"));
    }
}