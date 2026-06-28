package com.etoro.payment_gateway_app.exceptions;

public class InvalidStateTransitionException extends PaymentGatewayException{

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
