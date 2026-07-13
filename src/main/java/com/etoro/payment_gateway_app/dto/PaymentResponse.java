package com.etoro.payment_gateway_app.dto;

import com.etoro.payment_gateway_app.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;


@Setter
@Getter
@AllArgsConstructor
public class PaymentResponse {

    private PaymentStatus status;
    private BigDecimal amountConfirmed;
    private String orderId;
    private String customerId;
    private UUID paymentReference;
}
