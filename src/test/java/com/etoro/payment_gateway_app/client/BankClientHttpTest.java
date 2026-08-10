package com.etoro.payment_gateway_app.client;


import com.etoro.payment_gateway_app.dto.AuthorizationResponse;
import com.etoro.payment_gateway_app.dto.AuthorizeRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class BankClientHttpTest {

    private static WireMockServer wireMockServer = new WireMockServer(0);

    @Autowired
    private BankClientHttp bankClientHttp;

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();

        configureFor(
                "localhost",
                wireMockServer.port()
        );
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "bank.base-url",
                () -> "http://localhost:" + wireMockServer.port()
        );
    }

    @Test
    void shouldAuthorizePayment() {
        stubFor(post(urlEqualTo("/api/v1/authorizations"))
                .willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader(
                                        "Content-Type",
                                        "application/json"
                                )
                                .withBody("""
                                            {
                                                "authorization_id": "AUTH-123"
                                            }
                                        """)
                )
        );

        AuthorizeRequest request = new AuthorizeRequest(
                5000L,
                "4111111111111111",
                "123",
                12,
                28
        );

        String idempotencyKey = "key-123";

        AuthorizationResponse response = bankClientHttp.authorize(request, idempotencyKey);

        assertEquals("AUTH-123", response.getAuthorizationId());

        verify(
                postRequestedFor(urlEqualTo("/api/v1/authorizations"))
                        .withHeader("Idempotency-Key", equalTo(idempotencyKey))
        );
    }

    @Test
    void shouldRetryWhenBankReturnsServerError() {

        stubFor(post(urlEqualTo("/api/v1/authorizations"))
                        .inScenario("bank-retry")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                        )
                        .willSetStateTo("second-attempt")
        );

        stubFor(post(urlEqualTo("/api/v1/authorizations"))
                        .inScenario("bank-retry")
                        .whenScenarioStateIs("second-attempt")
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                        )
                        .willSetStateTo("third-attempt")
        );

        stubFor(post(urlEqualTo("/api/v1/authorizations"))
                .inScenario("bank-retry")
                .whenScenarioStateIs("third-attempt")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                          "authorization_id": "AUTH-RETRY-123"
                        }
                        """)
                )
        );

        AuthorizeRequest request = new AuthorizeRequest(
                5000L,
                "4111111111111111",
                "123",
                12,
                28
        );

        String idempotencyKey = "retry-test-123";

        AuthorizationResponse response = bankClientHttp.authorize(request, idempotencyKey);

        assertEquals("AUTH-RETRY-123", response.getAuthorizationId());

        verify(3, postRequestedFor(urlEqualTo("/api/v1/authorizations")));
    }
}
