package com.etoro.payment_gateway_app.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(BankProperties bankProperties) {

        SimpleClientHttpRequestFactory simpleClientHttpRequestFactory = new SimpleClientHttpRequestFactory();
        simpleClientHttpRequestFactory.setConnectTimeout(bankProperties.getConnectTimeout());
        simpleClientHttpRequestFactory.setReadTimeout(bankProperties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(bankProperties.getBaseUrl())
                .requestFactory(simpleClientHttpRequestFactory)
                .build();
    }
}