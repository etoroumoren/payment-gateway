package com.etoro.payment_gateway_app.dto;

import com.etoro.payment_gateway_app.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {

    private UUID paymentReference;
    private PaymentStatus status;
    private long amount;
    private String orderId;
    private String customerId;
}
