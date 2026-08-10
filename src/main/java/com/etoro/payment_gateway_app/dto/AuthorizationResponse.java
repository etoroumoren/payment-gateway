package com.etoro.payment_gateway_app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
@AllArgsConstructor
public class AuthorizationResponse {

    @JsonProperty("authorization_id")
    private String authorizationId;
    private String status;
    private Long amount;
}
