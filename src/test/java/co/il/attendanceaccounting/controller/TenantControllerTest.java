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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantControllerTest extends BaseApiControllerTest {

    private static final String TENANT_ADD_URL = "/account/tenant/add";
    private static final String NEW_COMPANY_NAME = "Initrode";
    private static final String NEW_CONTACT_NAME = "Bob Slydell";
    private static final String NEW_PHONE = "+1-555-0400";
    private static final String NEW_EMAIL = "admin@initrode.local";

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without any credentials (public) assigns an id and persists the organization")
    void addTenantTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_COMPANY_NAME, NEW_CONTACT_NAME, NEW_PHONE, NEW_EMAIL), null, null);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        TenantMappingDto body = readObject(response, TenantMappingDto.class);
        assertNotNull(body.getTenantId(), "Reason: the service assigns the identifier and must return it");
        assertTrue(body.getTenantId() > 300,
                "Reason: a generated id must not collide with the seeded organizations 100, 200 and 300");
        assertEquals(NEW_COMPANY_NAME, body.getCompanyName());
        assertEquals(NEW_CONTACT_NAME, body.getContactName());
        assertEquals(NEW_PHONE, body.getPhone());
        assertEquals(NEW_EMAIL, body.getEmail());
        assertNull(body.getStatus(), "Reason: status is not part of the add request, so it stays unset");

        TenantMapping stored = tenantMappingRepository.findById(body.getTenantId()).orElseThrow();
        assertEquals(NEW_COMPANY_NAME, stored.getCompanyName());
        assertEquals(NEW_CONTACT_NAME, stored.getContactName());
        assertEquals(NEW_PHONE, stored.getPhone());
        assertEquals(NEW_EMAIL, stored.getEmail());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add assigns a distinct id to each organization")
    void addTwoTenantsGetDistinctIdsTest() {
        ResponseEntity<String> first = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_COMPANY_NAME, NEW_CONTACT_NAME, NEW_PHONE, NEW_EMAIL), null, null);
        ResponseEntity<String> second = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto("Vandelay Industries", "Art Vandelay", "+1-555-0402",
                        "admin@vandelay.local"), null, null);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());

        Integer firstId = readObject(first, TenantMappingDto.class).getTenantId();
        Integer secondId = readObject(second, TenantMappingDto.class).getTenantId();
        assertTrue(secondId > firstId,
                String.format("Reason: ids must be assigned in sequence, got %s then %s", firstId, secondId));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add with a company name already registered returns 409 CONFLICT")
    void addExistingCompanyNameTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(TENANT_100_NAME, "Someone Else", "+1-555-9999", "other@acme.local"), null, null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("tenant exists", errorMessage(response));
        assertEquals(before, tenantMappingRepository.count(), "Reason: no organization should be added");
        assertEquals(TENANT_100_EMAIL, tenantMappingRepository.findById(TENANT_ID_100).orElseThrow().getEmail(),
                "Reason: a conflicting add must not overwrite the existing organization");
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add matches an existing company name regardless of case and returns 409")
    void addExistingCompanyNameDifferentCaseTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(TENANT_100_NAME.toUpperCase(), "Someone Else", "+1-555-9999",
                        "other@acme.local"), null, null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
                "Reason: login resolves a company name case-insensitively, so registration must reject it the same way");
        assertEquals(before, tenantMappingRepository.count());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without a companyName returns 400 BAD_REQUEST")
    void addTenantMissingCompanyNameTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(null, NEW_CONTACT_NAME, NEW_PHONE, NEW_EMAIL), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without a contactName returns 400 BAD_REQUEST")
    void addTenantMissingContactNameTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_COMPANY_NAME, null, NEW_PHONE, NEW_EMAIL), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without a phone returns 400 BAD_REQUEST")
    void addTenantMissingPhoneTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_COMPANY_NAME, NEW_CONTACT_NAME, null, NEW_EMAIL), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add without an email returns 400 BAD_REQUEST")
    void addTenantMissingEmailTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_COMPANY_NAME, NEW_CONTACT_NAME, NEW_PHONE, null), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /account/tenant/add with a malformed email returns 400 BAD_REQUEST")
    void addTenantInvalidEmailTest() {
        long before = tenantMappingRepository.count();
        ResponseEntity<String> response = send(HttpMethod.POST, TENANT_ADD_URL,
                createTenantAddDto(NEW_COMPANY_NAME, NEW_CONTACT_NAME, NEW_PHONE, "not-an-email"), null, null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(before, tenantMappingRepository.count());
    }
}
