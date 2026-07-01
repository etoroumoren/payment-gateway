package com.etoro.payment_gateway_app.exceptions;

import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends PaymentGatewayException{

    String message;

    public PaymentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
