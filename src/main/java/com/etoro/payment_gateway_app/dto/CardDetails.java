package com.etoro.payment_gateway_app.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CardDetails {

    @NotBlank
    private String cardNumber;

    @NotBlank
    private String expiryDate;

    @NotBlank
    private String cvv;

}
