package com.etoro.payment_gateway_app.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthorizePaymentRequest {

    @NotNull
    @Positive
    private Long amount;

    @NotNull
    @Valid
    private CardDetails cardDetails;

    @NotBlank
    private String orderId;

    @NotBlank
    private String customerId;

    @NotBlank
    private String currency;
}
