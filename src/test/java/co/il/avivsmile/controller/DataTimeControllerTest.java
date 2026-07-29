package co.il.avivsmile.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import co.il.avivsmile.dto.DataTimeDto;

class DataTimeControllerTest extends BaseApiControllerTest {

    private static final String RECORD_URL = "/record";
    private static final String START_URL = RECORD_URL + "/start/";
    private static final String FINISH_URL = RECORD_URL + "/finish/";
    private static final String RECORDS_URL = RECORD_URL + "/records";
    private static final String RANGE_RECORDS_URL = RECORD_URL + "/range/records";
    private static final String WORKDAYS_URL = RECORD_URL + "/workdays";
    private static final String HOURS_URL = RECORD_URL + "/hours";
    private static final String OVERTIME_URL = RECORD_URL + "/overtime";
    private static final String CHECK_URL = RECORD_URL + "/check";
    private static final String REMOVE_URL = RECORD_URL + "/remove/";

    private static final String RANGE_START = "2024-01-01";
    private static final String RANGE_END = "2024-01-31";

    private static final String SEEDED_DAY = "2024-01-02";
    private static final int SEEDED_TODAY_OPEN_ID = 1005;
    private static final int SEEDED_FINISHED_ID = 1002;
    private static final int SEEDED_OPEN_ID = 1003;

    private String range (String base, int idUser) {
        return base + "?startDate=" + RANGE_START + "&finishDate=" + RANGE_END + "&idUser=" + idUser;
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /record/start/{id} as the same user opens a record")
    void startRecordSelfTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, START_URL + USER_ID, null, USER_ID, USER_PWD);
        assertEquals (HttpStatus.OK, response.getStatusCode());
        DataTimeDto body = readObject(response, DataTimeDto.class);
        assertNotNull(body.getStart(), "Reason: start must be set");
        assertNull(body.getFinish(), "Reason: finish must be open");
        assertEquals (USER_ID, body.getUser().getIdUser().intValue());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /record/start/{id} for another user (non-admin) returns 403")
    void startRecordForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, START_URL + OTHER_USER_ID, null, USER_ID, USER_PWD);
        assertEquals (HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals (FORBIDDEN_MESSAGE, errorMessage (response));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /record/start/{id} for a missing user (admin) returns 400")
    void startRecordMissingUserTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, START_URL + "987654", null, ADMIN_ID, ADMIN_PWD);
        assertEquals (HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("User with login = 987654 not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /record/finish/{id} closes the open record for today (reused, not inserted)")
    void finishRecordSelfTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, FINISH_URL + USER_ID, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        DataTimeDto body = readObject(response, DataTimeDto.class);
        assertEquals(SEEDED_TODAY_OPEN_ID, body.getId().intValue(), "Reason: should reuse today's open");
        assertNotNull(body.getFinish(), "Reason: finish must now be set");
        assertNotNull(timeRepository.findById(SEEDED_TODAY_OPEN_ID).orElseThrow().getFinish());
    }

    @Test
    @FlywayTest
    @DisplayName("PUT /record/finish/{id} for another user (non-admin) returns 403")
    void finishRecordForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.PUT, FINISH_URL + OTHER_USER_ID, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /record as admin edits an existing record")
    void editRecordAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, RECORD_URL, createEditDataTimeUserDto(SEEDED_FINISHED_ID, null, LocalDateTime.parse("2024-01-03T18:00:00")), ADMIN_ID, ADMIN_PWD);
        assertEquals (HttpStatus.OK, response.getStatusCode());
        DataTimeDto body = readObject (response, DataTimeDto.class);
        assertEquals(LocalDateTime.parse("2024-01-03T18:00:00"), body.getFinish());
        assertEquals(18, timeRepository.findById(SEEDED_FINISHED_ID).orElseThrow().getFinish().getHour());
    }

    @Test
    @FlywayTest
    @DisplayName("POST /record as non-admin returns 403")
    void editRecordForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.POST, RECORD_URL,
                createEditDataTimeUserDto (SEEDED_FINISHED_ID, null, LocalDateTime.parse("2024-01-03T18:00:00")), USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals (FORBIDDEN_MESSAGE, errorMessage (response));
    }

    @Test
    @FlywayTest
    @DisplayName("POST /record for a missing record id returns 400")
    void editRecordMissingTest() {
        ResponseEntity<String> response = send (HttpMethod.POST, RECORD_URL,
                createEditDataTimeUserDto (999999, null, LocalDateTime.parse("2024-01-03T18:00:00")), ADMIN_ID, ADMIN_PWD);
        assertEquals (HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("idrow is not correct", errorMessage (response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/records?localDate=... as admin returns that day's records")
    void recordsByDayAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, RECORDS_URL + "?localDate=" + SEEDED_DAY, null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Integer> ids = readList(response, DataTimeDto.class).stream().map(DataTimeDto::getId).toList();
        assertTrue(ids.contains(1001));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/records?localDate=... as non-admin returns 403")
    void recordsByDayForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, RECORDS_URL + "?localDate=" + SEEDED_DAY, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/range/records as admin returns the user's records in range")
    void rangeRecordsAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(RANGE_RECORDS_URL, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Integer> ids = readList(response, DataTimeDto.class).stream().map(DataTimeDto::getId ).toList();
        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of (1001, 1002, 1003)));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/range/records as non-admin returns 403")
    void rangeRecordsForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range (RANGE_RECORDS_URL, USER_ID), null, USER_ID, USER_PWD);
        assertEquals (HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals (FORBIDDEN_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/range/records for a missing user (admin) returns 400")
    void rangeRecordsMissingUserTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(RANGE_RECORDS_URL, 987654), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("User with login = 987654 not found", errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/workdays as admin counts distinct worked days")
    void workdaysAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range (WORKDAYS_URL, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals (HttpStatus.OK, response.getStatusCode());
        assertEquals(3L, readObject(response, Long.class).longValue());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/workdays as non-admin returns 403")
    void workdaysForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(WORKDAYS_URL, USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals (FORBIDDEN_MESSAGE, errorMessage (response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/hours as admin sums worked minutes")
    void hoursAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(HOURS_URL, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(990L, readObject(response, Long.class).longValue());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/hours as non-admin returns 403")
    void hoursForbiddenTest() {
        ResponseEntity<String> response = send (HttpMethod. GET, range(HOURS_URL, USER_ID), null, USER_ID, USER_PWD);
        assertEquals (HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals (FORBIDDEN_MESSAGE, errorMessage (response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/overtime as admin sums minutes above 480/day")
    void overtimeAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(OVERTIME_URL, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(30L, readObject(response, Long.class).longValue());
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/overtime as non-admin returns 403")
    void overtimeForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(OVERTIME_URL, USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/check as admin returns rows with an open start/finish")
    void checkAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(CHECK_URL, USER_ID), null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Integer> ids = readList(response, DataTimeDto.class).stream().map(DataTimeDto::getId).toList();
        assertEquals(1, ids.size());
        assertTrue(ids.contains(SEEDED_OPEN_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("GET /record/check as non-admin returns 403")
    void checkForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.GET, range(CHECK_URL, USER_ID), null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /record/remove/{id} as admin removes the record")
    void removeRecordAsAdminTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, REMOVE_URL + SEEDED_FINISHED_ID, null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        DataTimeDto body = readObject(response, DataTimeDto.class);
        assertEquals(SEEDED_FINISHED_ID, body.getId().intValue());
        assertFalse(timeRepository.existsById(SEEDED_FINISHED_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /record/remove/{id} as non-admin returns 403")
    void removeRecordForbiddenTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, REMOVE_URL + SEEDED_FINISHED_ID, null, USER_ID, USER_PWD);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(FORBIDDEN_MESSAGE, errorMessage(response));
        assertTrue(timeRepository.existsById(SEEDED_FINISHED_ID));
    }

    @Test
    @FlywayTest
    @DisplayName("DELETE /record/remove/{id} for a missing record returns 400")
    void removeRecordMissingTest() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, REMOVE_URL + "999999", null, ADMIN_ID, ADMIN_PWD);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("idrow is not correct", errorMessage(response));
    }
}