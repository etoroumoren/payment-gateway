package com.etoro.payment_gateway_app.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthorizePaymentRequest {

    private long amount;
    private CardDetails cardDetails;
    private String orderId;
    private String customerId;
    private String currency;
}
