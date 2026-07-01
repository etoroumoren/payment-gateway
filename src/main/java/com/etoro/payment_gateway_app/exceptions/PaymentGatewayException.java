package com.etoro.payment_gateway_app.exceptions;

import org.springframework.http.HttpStatus;

public class PaymentGatewayException extends RuntimeException{
    private HttpStatus httpStatus;

    public PaymentGatewayException(String message, HttpStatus status) {
        super(message);
        this.httpStatus = status;
    }


    public PaymentGatewayException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.httpStatus = status;
    }

    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }
}
