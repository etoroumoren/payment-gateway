package com.etoro.payment_gateway_app.exceptions;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PaymentNotFoundException extends PaymentGatewayException{

    public PaymentNotFoundException(UUID paymentReference) {
        super("Payment not found with reference: " + paymentReference, HttpStatus.NOT_FOUND);
    }

    public PaymentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
