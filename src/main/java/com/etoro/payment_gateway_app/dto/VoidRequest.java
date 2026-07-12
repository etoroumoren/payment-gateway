package com.etoro.payment_gateway_app.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class VoidRequest {

    private String authorizationId;
}
