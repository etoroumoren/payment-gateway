package com.etoro.payment_gateway_app.service;


import com.etoro.payment_gateway_app.model.IdempotencyKey;
import com.etoro.payment_gateway_app.repository.IdempotencyRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> findCachedResponse(String idempotencyKey, Class<T> responseType) {
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if(existingKey.isEmpty()) {
            return Optional.empty();
        }

         try {
             T response = objectMapper.readValue(
                        existingKey.get().getResponseBody(),
                        responseType
                );

             return Optional.of(response);

         } catch (JacksonException e) {
             throw new IllegalStateException("Couldn't process checks", e);
        }
    }

    public void saveKey(String idempotencyKey, UUID paymentId, String operation, Object responseBody) {
        try {
            IdempotencyKey key = new IdempotencyKey();

            key.setIdempotencyKey(idempotencyKey);
            key.setPaymentId(paymentId);
            key.setOperation(operation);
            key.setResponseBody(
                    objectMapper.writeValueAsString(responseBody)
            );

            idempotencyRepository.save(key);
        } catch (JacksonException e) {
            throw new IllegalStateException("Couldn't process checks", e);
        }
    }
}
