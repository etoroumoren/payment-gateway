package com.etoro.payment_gateway_app.exceptions;

public class BankRejectedException extends PaymentGatewayException{

    public BankRejectedException(String message) {
        super(message);
    }
}
