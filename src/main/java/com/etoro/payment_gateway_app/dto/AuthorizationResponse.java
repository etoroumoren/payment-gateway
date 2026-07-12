package com.etoro.payment_gateway_app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
@AllArgsConstructor
public class AuthorizationResponse {

    private String authorizationId;
    private String status;
    private BigDecimal amount;
}
