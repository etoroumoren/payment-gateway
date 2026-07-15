package com.etoro.payment_gateway_app.service;


import com.etoro.payment_gateway_app.client.BankClient;
import com.etoro.payment_gateway_app.dto.AuthorizationResponse;
import com.etoro.payment_gateway_app.dto.AuthorizeRequest;
import com.etoro.payment_gateway_app.dto.PaymentResponse;
import com.etoro.payment_gateway_app.model.IdempotencyKey;
import com.etoro.payment_gateway_app.model.Payment;
import com.etoro.payment_gateway_app.model.PaymentStatus;
import com.etoro.payment_gateway_app.repository.IdempotencyRepository;
import com.etoro.payment_gateway_app.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentService {

    private final IdempotencyRepository idempotencyRepository;
    private final PaymentRepository paymentRepository;
    private final BankClient bankClient;

    public PaymentService(
            IdempotencyRepository idempotencyRepository,
            PaymentRepository paymentRepository,
            BankClient bankClient
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.paymentRepository = paymentRepository;
        this.bankClient = bankClient;
    }

    @Transactional
    public PaymentResponse authorize(AuthorizeRequest request, String idempotecyKey) {
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotecyKey);

        if(existingKey.isPresent()){
            return new PaymentResponse();
        } else {
            Payment payment = new Payment();
            payment.setOrderId(request.getOrderId());
            payment.setCustomerId(request.getCustomerId());
            payment.setAmount(request.getAmount());
            payment.setCurrency(request.getCurrency());
            payment.setCardNumber(request.getCardDetails().getCardNumber());
            payment.setCardExpiry(request.getCardDetails().getExpiryDate());
            payment.setCardCvv(request.getCardDetails().getCvv());
            payment.setStatus(PaymentStatus.PENDING);

            AuthorizationResponse response = bankClient.authorize(request, idempotecyKey);

            payment.setBankAuthId(response.getAuthorizationId());
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setAuthorizedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            IdempotencyKey idempotencyKey = new IdempotencyKey();

        }
    }
}
