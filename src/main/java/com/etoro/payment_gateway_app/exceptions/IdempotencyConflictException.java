package com.etoro.payment_gateway_app.exceptions;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends PaymentGatewayException{

    public IdempotencyConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
