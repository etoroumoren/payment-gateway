package com.etoro.payment_gateway_app.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends PaymentGatewayException{

    public InvalidStateTransitionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}