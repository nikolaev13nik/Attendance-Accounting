package co.il.attendanceaccounting.controller;

import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.dto.TenantPageDto;
import co.il.attendanceaccounting.model.TenantMapping;

import static co.il.attendanceaccounting.exceptions.ErrorConstants.INVALID_PAGE_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantListControllerTest extends BaseApiControllerTest {

    private static final String TENANTS_URL = "/account/tenants";
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenants as platform admin returns the first page of organizations ordered by tenant id")
    void listTenantsAsPlatformAdminTest() {
        ResponseEntity<String> response = sendWithToken(HttpMethod.GET, TENANTS_URL, null, platformAdminToken());

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Reason: the platform admin may list all organizations");
        TenantPageDto tenantPage = readObject(response, TenantPageDto.class);
        assertEquals(storedTenantIds(), tenantIds(tenantPage),
                "Reason: the default page of 20 holds every seeded organization, ordered by tenant id");
        assertEquals(0, tenantPage.getPage(), "Reason: the first page is page 0");
        assertEquals(DEFAULT_PAGE_SIZE, tenantPage.getSize(), "Reason: the default page size is 20");
        assertEquals(tenantMappingRepository.count(), tenantPage.getTotalElements(),
                "Reason: the total counts every stored organization");
        assertEquals(1, tenantPage.getTotalPages(), "Reason: the seeded organizations fit on one page");
        TenantMappingDto acme = tenantPage.getItems().stream()
                .filter(tenant -> TENANT_ID_100.equals(tenant.getTenantId()))
                .findFirst()
                .orElseThrow();
        assertEquals(TENANT_100_NAME, acme.getCompanyName(), "Reason: the company name is mapped");
        assertEquals(TENANT_100_CONTACT, acme.getContactName(), "Reason: the contact name is mapped");
        assertEquals(TENANT_100_PHONE, acme.getPhone(), "Reason: the phone is mapped");
        assertEquals(TENANT_100_EMAIL, acme.getEmail(), "Reason: the email is mapped");
        assertEquals(TENANT_100_STATUS, acme.getStatus(), "Reason: the status is mapped");
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenants?page&size splits the organizations into consecutive pages without overlap")
    void listTenantsPagedTest() {
        String token = platformAdminToken();
        List<Integer> stored = storedTenantIds();
        int expectedPages = (stored.size() + 1) / 2;

        TenantPageDto first = readObject(sendWithToken(HttpMethod.GET, pageUrl(0, 2), null, token), TenantPageDto.class);
        TenantPageDto second = readObject(sendWithToken(HttpMethod.GET, pageUrl(1, 2), null, token), TenantPageDto.class);

        assertEquals(stored.subList(0, 2), tenantIds(first), "Reason: page 0 holds the two lowest tenant ids");
        assertEquals(stored.subList(2, Math.min(4, stored.size())), tenantIds(second),
                "Reason: page 1 continues right after page 0");
        assertEquals(stored.size(), first.getTotalElements(), "Reason: the total does not depend on the page");
        assertEquals(expectedPages, first.getTotalPages(), "Reason: the page count follows the page size");
        assertEquals(1, second.getPage(), "Reason: the response names the page it holds");
        assertEquals(2, second.getSize(), "Reason: the response names the requested page size");
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenants past the last page returns an empty page with the real totals")
    void listTenantsPastLastPageTest() {
        ResponseEntity<String> response = sendWithToken(HttpMethod.GET, pageUrl(50, 20), null, platformAdminToken());

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Reason: an empty page is a valid answer, not an error");
        TenantPageDto tenantPage = readObject(response, TenantPageDto.class);
        assertTrue(tenantPage.getItems().isEmpty(), "Reason: there are no organizations past the last page");
        assertEquals(tenantMappingRepository.count(), tenantPage.getTotalElements(),
                "Reason: the total still tells the caller how many organizations exist");
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenants with size 0, size 101 or page -1 returns 400 Bad Request")
    void listTenantsInvalidPagingTest() {
        String token = platformAdminToken();

        ResponseEntity<String> emptyPage = sendWithToken(HttpMethod.GET, pageUrl(0, 0), null, token);
        assertEquals(HttpStatus.BAD_REQUEST, emptyPage.getStatusCode(), "Reason: a page must hold at least one organization");
        assertEquals(String.format(INVALID_PAGE_MSG, 0, 0), errorMessage(emptyPage),
                "Reason: the message names the rejected page and size");
        assertEquals(HttpStatus.BAD_REQUEST, sendWithToken(HttpMethod.GET, pageUrl(0, 101), null, token).getStatusCode(),
                "Reason: the page size is capped at 100");
        assertEquals(HttpStatus.BAD_REQUEST, sendWithToken(HttpMethod.GET, pageUrl(-1, 20), null, token).getStatusCode(),
                "Reason: page numbers start at 0");
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenants as a company administrator returns 403 Forbidden")
    void listTenantsAsAdministratorForbiddenTest() {
        long countBefore = tenantMappingRepository.count();

        ResponseEntity<String> response = send(HttpMethod.GET, TENANTS_URL, null, ADMIN_ID, ADMIN_PWD);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Reason: a company administrator must not see other organizations");
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response),
                "Reason: every 403 carries the shared message");
        assertEquals(countBefore, tenantMappingRepository.count(), "Reason: listing never changes organizations");
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenants as a plain user returns 403 Forbidden")
    void listTenantsAsUserForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, TENANTS_URL, null, USER_ID, USER_PWD);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "Reason: only the platform admin may list organizations");
    }

    @Test
    @FlywayTest
    @DisplayName("GET /account/tenants without a token returns 401 Unauthorized")
    void listTenantsAnonymousUnauthorizedTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, TENANTS_URL, null, null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "Reason: listing organizations needs a token");
    }

    private String pageUrl(int page, int size) {
        return TENANTS_URL + "?page=" + page + "&size=" + size;
    }

    private List<Integer> storedTenantIds() {
        return tenantMappingRepository.findAll(Sort.by("tenantId")).stream()
                .map(TenantMapping::getTenantId)
                .toList();
    }

    private static List<Integer> tenantIds(TenantPageDto tenantPage) {
        return tenantPage.getItems().stream().map(TenantMappingDto::getTenantId).toList();
    }
}
