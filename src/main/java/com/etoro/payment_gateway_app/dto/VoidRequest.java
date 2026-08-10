package com.etoro.payment_gateway_app.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class VoidRequest {

    @JsonProperty("authorization_id")
    private String authorizationId;
}
