package com.etoro.payment_gateway_app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
public class AuthorizeRequest {

    private Long amount;

    @JsonProperty("card_number")
    private String cardNumber;

    private String cvv;

    @JsonProperty("expiry_month")
    private int expiryMonth;

    @JsonProperty("expiry_year")
    private int expiryYear;
}
