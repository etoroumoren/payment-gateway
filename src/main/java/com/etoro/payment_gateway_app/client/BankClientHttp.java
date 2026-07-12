package com.etoro.payment_gateway_app.client;

import com.etoro.payment_gateway_app.dto.*;
import com.etoro.payment_gateway_app.exceptions.BankRejectedException;
import com.etoro.payment_gateway_app.exceptions.BankTransientException;
import com.etoro.payment_gateway_app.exceptions.PaymentGatewayException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;


@Service
public class BankClientHttp implements BankClient{


    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private final RestClient restClient;

    public BankClientHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AuthorizationResponse authorize(AuthorizeRequest request, String idempotencyKey) {

        try {
            return restClient.post()
                    .uri("/api/v1/authorizations")
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BankRejectedException("Bank rejected: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BankTransientException("Bank server error: " + res.getStatusCode());
                    })
                    .body(AuthorizationResponse.class);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BankTransientException("Bank unreachable", ex);
        }
    }


    @Override
    public CaptureResponse capture(String authorizationId, String idempotencyKey) {

        try {
            return restClient.post()
                    .uri("/api/v1/captures")
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CaptureRequest(authorizationId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BankRejectedException("Bank server error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BankTransientException("Bank server error: " + res.getStatusCode());
                    })
                    .body(CaptureResponse.class);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BankTransientException("Bank unreachable", ex);
        }
    }


    @Override
    public VoidResponse voidAuthorization(String authorizationId, String idempotencyKey) {

        try {
            return restClient.post()
                    .uri("/api/v1/voids")
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VoidRequest(authorizationId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BankRejectedException("Bank server error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BankTransientException("Bank server error: " + res.getStatusCode());
                    })
                    .body(VoidResponse.class);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BankTransientException("Bank unreachable", ex);
        }
    }

    @Override
    public RefundResponse refund(String authorizationId, String idempotencyKey) {

        try {
            return restClient.post()
                    .uri("/api/v1/refunds")
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RefundRequest(authorizationId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BankRejectedException("Bank server error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BankTransientException("Bank server error: " + res.getStatusCode());
                    })
                    .body(RefundResponse.class);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BankTransientException("Bank unreachable", ex);
        }
    }
}
