package com.etoro.payment_gateway_app.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class CaptureRequest {

    private Long amount;

    @JsonProperty("authorization_id")
    private String authorizationId;
}
