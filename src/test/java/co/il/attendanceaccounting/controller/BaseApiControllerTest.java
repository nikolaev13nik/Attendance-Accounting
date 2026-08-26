package co.il.attendanceaccounting.controller;

import java.time.LocalDateTime;
import java.util.List;

import co.il.attendanceaccounting.security.dto.LoginRequestDto;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.dao.UserTimeRepository;
import co.il.attendanceaccounting.dto.UserEditDto;
import co.il.attendanceaccounting.dto.UserRegisterDto;
import co.il.attendanceaccounting.dto.EditDataTimeUserDto;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestExecutionListeners(
        listeners = { FlywayTestExecutionListener.class },
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
public abstract class BaseApiControllerTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserTimeRepository timeRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static final Integer ADMIN_ID = 1;
    protected static final String ADMIN_PWD = "admin123";
    protected static final Integer USER_ID = 2;
    protected static final String USER_PWD = "user123";
    protected static final Integer OTHER_USER_ID = 3;
    protected static final String OTHER_USER_PWD = "other123";

    protected static final String FORBIDDEN_MESSAGE = "Forbidden";

    private final RestTemplate rest = createRestTemplate();

    @Bean
    private static RestTemplate createRestTemplate() {
            RestTemplate rt = new RestTemplate();
            rt.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response){
                    return false;
                }
            });
            return rt;
    }

    /** Helper to send HTTP requests with optional Basic Authentication */
    protected ResponseEntity<String> send(
            HttpMethod method, String path, Object body, Integer secureUser, String securePass) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (secureUser != null ) {
            headers.setBearerAuth(obtainToken(secureUser, securePass));
        }
        Object payload = (body == null || body instanceof  String) ? body: objectMapper.writeValueAsString(body);
        return rest.exchange(url(path), method, new HttpEntity<>(payload,headers), String.class);
    }

    protected String url(String path){
        return "http://localhost:" + port + path;
    }

    private String obtainToken(Integer secureUser, String securePass) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = objectMapper.writeValueAsString(new LoginRequestDto(secureUser,securePass));
        ResponseEntity<String>response = rest.exchange(url("/account/login"),HttpMethod.POST,new HttpEntity<>(payload,headers),String.class);
        return json(response).path("token").asString();
    }

    /** Deserialize the response body into a single Object */
    protected <T> T readObject(ResponseEntity<String> response, Class<T> clazz) {
        try {
            return objectMapper.readValue(response.getBody(), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON object", e);
        }
    }

    /** Deserialize the response body into a List of the given element type */
    protected <T> List<T> readList(ResponseEntity<String> response, Class<T> elementType) {
        try {
            return objectMapper.readValue(
                    response.getBody(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON list", e);
        }
    }

    /** Parse the raw string response body to a Jackson JsonNode */
    protected JsonNode json(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response body to JSON tree", e);
        }
    }

    /** Retrieve message field from a structured error response */
    protected String errorMessage(ResponseEntity<String> response) {
        try {
            JsonNode node = json(response);
            if (node.has("message")) {
                return node.get("message").asText();
            }
            return response.getBody();
        } catch (Exception e) {
            return response.getBody();
        }
    }

    // Helper builders matching Dto properties
    protected UserRegisterDto createUserRegisterDto(Integer idUser, String password, String firstName, String lastName) {
        return UserRegisterDto.builder()
                .idUser(idUser)
                .password(password)
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }

    protected UserEditDto createUserEditDto(String firstName, String lastName, String password) {
        return UserEditDto.builder()
                .firstName(firstName)
                .lastName(lastName)
                .password(password)
                .build();
    }

    protected EditDataTimeUserDto createEditDataTimeUserDto(Integer id, LocalDateTime start, LocalDateTime finish) {
        return EditDataTimeUserDto.builder()
                .id(id)
                .start(start)
                .finish(finish)
                .build();
    }
}