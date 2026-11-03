package co.il.attendanceaccounting.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

final class FakeTurnstile {

    static final String VERIFY_PATH = "/turnstile/v0/siteverify";
    static final String VALID_TOKEN = "valid-captcha-token";
    static final String REJECTED_TOKEN = "rejected-captcha-token";
    static final String UNAVAILABLE_TOKEN = "unavailable-captcha-token";

    private static final WireMockServer SERVER = start();

    private FakeTurnstile() {
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo(VERIFY_PATH)).atPriority(10)
                .willReturn(okJson("{\"success\":true,\"error-codes\":[]}")));
        server.stubFor(post(urlEqualTo(VERIFY_PATH)).atPriority(1)
                .withRequestBody(containing("response=" + REJECTED_TOKEN))
                .willReturn(okJson("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}")));
        server.stubFor(post(urlEqualTo(VERIFY_PATH)).atPriority(1)
                .withRequestBody(containing("response=" + UNAVAILABLE_TOKEN))
                .willReturn(aResponse().withStatus(503)));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }

    static String verifyUrl() {
        return SERVER.baseUrl() + VERIFY_PATH;
    }

    static WireMockServer server() {
        return SERVER;
    }
}
