package com.etoro.payment_gateway_app.client;

import com.etoro.payment_gateway_app.dto.*;

public interface BankClient {

    AuthorizationResponse authorize(AuthorizeRequest request, String idempotencyKey);

    CaptureResponse capture(String authorizationId, String idempotencyKey);

    VoidResponse voidAuthorization(String authorizationId, String idempotencyKey);

    RefundResponse refund(String authorizationId, String idempotencyKey);
}
