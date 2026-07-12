package com.etoro.payment_gateway_app.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class CaptureRequest {

    private String authorizationId;
}
