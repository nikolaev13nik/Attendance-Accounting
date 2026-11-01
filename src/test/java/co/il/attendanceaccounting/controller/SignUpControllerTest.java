package co.il.attendanceaccounting.controller;

import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import co.il.attendanceaccounting.dto.SignUpRequestDto;
import co.il.attendanceaccounting.dto.SignUpResponseDto;
import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.model.TenantMapping;
import co.il.attendanceaccounting.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignUpControllerTest extends BaseApiControllerTest {

    private static final String SIGN_UP_URL = "/account/signup";
    private static final String NEW_COMPANY_NAME = "Initrode";
    private static final String NEW_PHONE = "+1-555-0400";
    private static final Integer NEW_ADMIN_ID = 400;
    private static final String NEW_ADMIN_PWD = "initrode-pw";
    private static final String NEW_FIRST_NAME = "Bob";
    private static final String NEW_LAST_NAME = "Slydell";
    private static final String NEW_EMAIL = "bob@initrode.local";

    private SignUpRequestDto initrode() {
        return createSignUpRequestDto(NEW_COMPANY_NAME, NEW_PHONE, NEW_ADMIN_ID, NEW_ADMIN_PWD, NEW_FIRST_NAME,
                NEW_LAST_NAME, NEW_EMAIL);
    }

    private void assertNothingCreated(long tenantsBefore, long usersBefore) {
        assertEquals(tenantsBefore, tenantMappingRepository.count(), "Reason: no organization should be added");
        assertEquals(usersBefore, userRepository.count(), "Reason: no user should be added");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup without credentials creates the organization and its administrator together")
    void signUpTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, SIGN_UP_URL, initrode(), null, null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        SignUpResponseDto body = readObject(response, SignUpResponseDto.class);
        TenantMappingDto tenant = body.getTenant();
        assertNotNull(tenant.getTenantId(), "Reason: the service assigns the identifier and must return it");
        assertTrue(tenant.getTenantId() > 300,
                "Reason: a generated id must not collide with the seeded organizations 100, 200 and 300");
        assertEquals(NEW_COMPANY_NAME, tenant.getCompanyName());
        assertEquals(NEW_FIRST_NAME + " " + NEW_LAST_NAME, tenant.getContactName(),
                "Reason: the administrator is the organization's contact person");
        assertEquals(NEW_PHONE, tenant.getPhone());
        assertEquals(NEW_EMAIL, tenant.getEmail(), "Reason: the administrator's email is the contact email");
        assertNull(tenant.getStatus());

        UserProfileDto admin = body.getAdmin();
        assertEquals(NEW_ADMIN_ID, admin.getIdUser());
        assertEquals(tenant.getTenantId(), admin.getTenantId());
        assertEquals(Set.of("USER", "ADMINISTRATOR"), admin.getRoles());

        TenantMapping storedTenant = tenantMappingRepository.findById(tenant.getTenantId()).orElseThrow();
        assertEquals(NEW_COMPANY_NAME, storedTenant.getCompanyName());
        User storedAdmin = userRepository.findById(NEW_ADMIN_ID).orElseThrow();
        assertEquals(tenant.getTenantId(), storedAdmin.getTenantId());
        assertEquals(Set.of("USER", "ADMINISTRATOR"), storedAdmin.getRoles());
        assertNotEquals(NEW_ADMIN_PWD, storedAdmin.getPassword(), "Reason: the password must be stored hashed");

        String token = obtainToken(NEW_ADMIN_ID, NEW_ADMIN_PWD, tenant.getTenantId());
        ResponseEntity<String> users = sendWithToken(HttpMethod.GET,
                "/account/tenant/" + tenant.getTenantId() + "/users", null, token);
        assertEquals(HttpStatus.OK, users.getStatusCode(),
                "Reason: the new administrator must be able to log in and work in its organization immediately");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup assigns a distinct id to each organization")
    void signUpTwoOrganizationsGetDistinctIdsTest() {
        ResponseEntity<String> first = send(HttpMethod.POST, SIGN_UP_URL, initrode(), null, null);
        ResponseEntity<String> second = send(HttpMethod.POST, SIGN_UP_URL,
                createSignUpRequestDto("Vandelay Industries", "+1-555-0402", 401, "vandelay-pw", "Art", "Vandelay",
                        "art@vandelay.local"), null, null);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertNotEquals(readObject(first, SignUpResponseDto.class).getTenant().getTenantId(),
                readObject(second, SignUpResponseDto.class).getTenant().getTenantId());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup with a registered company name, in any case, returns 409 and creates nothing")
    void signUpExistingCompanyNameTest() {
        long tenantsBefore = tenantMappingRepository.count();
        long usersBefore = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, SIGN_UP_URL,
                createSignUpRequestDto("  " + TENANT_100_NAME.toUpperCase() + " ", NEW_PHONE, NEW_ADMIN_ID,
                        NEW_ADMIN_PWD, NEW_FIRST_NAME, NEW_LAST_NAME, NEW_EMAIL), null, null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("tenant exists", errorMessage(response));
        assertNothingCreated(tenantsBefore, usersBefore);
        assertFalse(userRepository.existsById(NEW_ADMIN_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup with a taken user id returns 409 and leaves no organization behind")
    void signUpTakenUserIdTest() {
        long tenantsBefore = tenantMappingRepository.count();
        long usersBefore = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, SIGN_UP_URL,
                createSignUpRequestDto(NEW_COMPANY_NAME, NEW_PHONE, USER_ID, NEW_ADMIN_PWD, NEW_FIRST_NAME,
                        NEW_LAST_NAME, NEW_EMAIL), null, null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("user exists", errorMessage(response));
        assertNothingCreated(tenantsBefore, usersBefore);
        assertFalse(tenantMappingRepository.existsByCompanyNameIgnoreCase(NEW_COMPANY_NAME),
                "Reason: an organization without its administrator is exactly the claimable state signup prevents");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup without a company name returns 400 and creates nothing")
    void signUpMissingCompanyNameTest() {
        assertRejected(createSignUpRequestDto(null, NEW_PHONE, NEW_ADMIN_ID, NEW_ADMIN_PWD, NEW_FIRST_NAME,
                NEW_LAST_NAME, NEW_EMAIL));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup without a phone returns 400 and creates nothing")
    void signUpMissingPhoneTest() {
        assertRejected(createSignUpRequestDto(NEW_COMPANY_NAME, null, NEW_ADMIN_ID, NEW_ADMIN_PWD, NEW_FIRST_NAME,
                NEW_LAST_NAME, NEW_EMAIL));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup without the administrator's last name returns 400 and creates nothing")
    void signUpMissingLastNameTest() {
        assertRejected(createSignUpRequestDto(NEW_COMPANY_NAME, NEW_PHONE, NEW_ADMIN_ID, NEW_ADMIN_PWD,
                NEW_FIRST_NAME, null, NEW_EMAIL));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup without a password returns 400 and creates nothing")
    void signUpMissingPasswordTest() {
        assertRejected(createSignUpRequestDto(NEW_COMPANY_NAME, NEW_PHONE, NEW_ADMIN_ID, null, NEW_FIRST_NAME,
                NEW_LAST_NAME, NEW_EMAIL));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/signup with a malformed email returns 400 and creates nothing")
    void signUpInvalidEmailTest() {
        assertRejected(createSignUpRequestDto(NEW_COMPANY_NAME, NEW_PHONE, NEW_ADMIN_ID, NEW_ADMIN_PWD,
                NEW_FIRST_NAME, NEW_LAST_NAME, "not-an-email"));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add no longer exists: an anonymous call is rejected and creates nothing")
    void addTenantRemovedTest() {
        long tenantsBefore = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, "/account/tenant/add",
                "{\"companyName\":\"Empty Shell\",\"contactName\":\"Nobody\",\"phone\":\"1\",\"email\":\"a@b.c\"}",
                null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Reason: an organization without an administrator must never be creatable anonymously");
        assertEquals(tenantsBefore, tenantMappingRepository.count());
    }

    private void assertRejected(SignUpRequestDto request) {
        long tenantsBefore = tenantMappingRepository.count();
        long usersBefore = userRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, SIGN_UP_URL, request, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNothingCreated(tenantsBefore, usersBefore);
    }
}
