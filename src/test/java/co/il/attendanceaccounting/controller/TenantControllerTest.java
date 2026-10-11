package co.il.attendanceaccounting.controller;

import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.model.TenantMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantControllerTest extends BaseApiControllerTest {

    private static final String TENANT_ADD_URL = "/account/tenant/add";
    private static final Integer NEW_TENANT_ID = 400;
    private static final String NEW_TENANT_NAME = "Initrode";
    private static final String NEW_TENANT_EMAIL = "admin@initrode.local";

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add as an authenticated user adds the tenant and persists it")
    void addTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_TENANT_ID, NEW_TENANT_NAME, NEW_TENANT_EMAIL), USER_ID, USER_PWD);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        TenantMappingDto body = readObject(response, TenantMappingDto.class);
        assertEquals(NEW_TENANT_ID, body.getTenantId());
        assertEquals(NEW_TENANT_NAME, body.getTenantName());
        assertEquals(NEW_TENANT_EMAIL, body.getEmail());
        assertNull(body.getStatus(), "Reason: status is not part of the add request, so it stays unset");

        TenantMapping stored = tenantMappingRepository.findById(NEW_TENANT_ID).orElseThrow();
        assertEquals(NEW_TENANT_NAME, stored.getTenantName());
        assertEquals(NEW_TENANT_EMAIL, stored.getEmail());
        assertNull(stored.getStatus());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add with an existing tenantId returns 409 CONFLICT")
    void addExistingTenantTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(TENANT_ID_100, "Acme Renamed", "other@acme.local"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("tenant exists", errorMessage(response));
        assertEquals(before, tenantMappingRepository.count(), "Reason: no tenant should be added");
        assertEquals(TENANT_100_NAME, tenantMappingRepository.findById(TENANT_ID_100).orElseThrow().getTenantName(),
                "Reason: a conflicting add must not overwrite the existing tenant");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without a token returns 401 UNAUTHORIZED")
    void addTenantUnauthorizedTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_TENANT_ID, NEW_TENANT_NAME, NEW_TENANT_EMAIL), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count(), "Reason: no tenant should be added");
        assertFalse(tenantMappingRepository.existsById(NEW_TENANT_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without a tenantId returns 400 BAD_REQUEST")
    void addTenantMissingTenantIdTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(null, NEW_TENANT_NAME, NEW_TENANT_EMAIL), USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count(), "Reason: no tenant should be added");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without a tenantName returns 400 BAD_REQUEST")
    void addTenantMissingTenantNameTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_TENANT_ID, null, NEW_TENANT_EMAIL), USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count(), "Reason: no tenant should be added");
        assertFalse(tenantMappingRepository.existsById(NEW_TENANT_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without an email returns 400 BAD_REQUEST")
    void addTenantMissingEmailTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_TENANT_ID, NEW_TENANT_NAME, null), USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count(), "Reason: no tenant should be added");
        assertFalse(tenantMappingRepository.existsById(NEW_TENANT_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add with a malformed email returns 400 BAD_REQUEST")
    void addTenantInvalidEmailTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_TENANT_ID, NEW_TENANT_NAME, "not-an-email"), USER_ID, USER_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count(), "Reason: no tenant should be added");
        assertFalse(tenantMappingRepository.existsById(NEW_TENANT_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add keeps the seeded tenants readable")
    void addTenantKeepsSeededTenantsTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_TENANT_ID, NEW_TENANT_NAME, NEW_TENANT_EMAIL), USER_ID, USER_PWD);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        TenantMapping seeded = tenantMappingRepository.findById(TENANT_ID_100).orElseThrow();
        assertEquals(TENANT_100_NAME, seeded.getTenantName());
        assertEquals(TENANT_100_EMAIL, seeded.getEmail());
        assertEquals(TENANT_100_STATUS, seeded.getStatus());
        assertTrue(tenantMappingRepository.existsById(OTHER_USER_TENANT_ID));
    }
}
