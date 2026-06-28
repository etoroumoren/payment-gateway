package com.etoro.payment_gateway_app.exceptions;

public class IdempotencyConflictException extends PaymentGatewayException{

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
