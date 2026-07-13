package com.etoro.payment_gateway_app.service;


import com.etoro.payment_gateway_app.dto.AuthorizeRequest;
import com.etoro.payment_gateway_app.dto.PaymentResponse;
import com.etoro.payment_gateway_app.repository.IdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    public final IdempotencyRepository idempotencyRepository;

    public PaymentService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public PaymentResponse authorize(AuthorizeRequest request, String idempotecyKey) {

        if(idempotencyRepository.findByIdempotencyKey(idempotecyKey)){

        }
    }
}
