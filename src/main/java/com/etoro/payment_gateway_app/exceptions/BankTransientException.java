package com.etoro.payment_gateway_app.exceptions;

import org.springframework.http.HttpStatus;

public class BankTransientException extends PaymentGatewayException{

    public BankTransientException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public BankTransientException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, cause);
    }
}
