package com.etoro.payment_gateway_app.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


@Configuration
@ConfigurationProperties(prefix = "bank")
@Getter
@Setter
public class BankProperties {

    private String baseUrl;
    private Duration connectTimeout;
    private Duration readTimeout;


}
