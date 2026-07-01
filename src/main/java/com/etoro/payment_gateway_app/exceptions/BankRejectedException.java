package com.etoro.payment_gateway_app.exceptions;

import org.springframework.http.HttpStatus;

public class BankRejectedException extends PaymentGatewayException{

    public BankRejectedException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
