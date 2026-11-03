package co.il.attendanceaccounting.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

import co.il.attendanceaccounting.configuration.AccountConfiguration;
import co.il.attendanceaccounting.exceptions.CaptchaRejectedException;
import co.il.attendanceaccounting.exceptions.CaptchaUnavailableException;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Component
public class CaptchaVerifier {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TurnstileResult(boolean success) {
    }

    private final RestClient restClient;
    private final String secret;

    public CaptchaVerifier(AccountConfiguration configuration) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(configuration.getCaptchaVerifyUrl())
                .requestFactory(requestFactory)
                .build();
        this.secret = configuration.getCaptchaSecret();
    }

    public void verify(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        TurnstileResult result;
        try {
            result = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TurnstileResult.class);
        } catch (RestClientException e) {
            throw new CaptchaUnavailableException(e);
        }
        if (result == null || isFalse(result.success())) {
            throw new CaptchaRejectedException();
        }
    }
}
