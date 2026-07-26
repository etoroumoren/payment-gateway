package com.etoro.payment_gateway_app.service;


import com.etoro.payment_gateway_app.client.BankClient;
import com.etoro.payment_gateway_app.dto.AuthorizationResponse;
import com.etoro.payment_gateway_app.dto.AuthorizePaymentRequest;
import com.etoro.payment_gateway_app.dto.AuthorizeRequest;
import com.etoro.payment_gateway_app.dto.PaymentResponse;
import com.etoro.payment_gateway_app.model.IdempotencyKey;
import com.etoro.payment_gateway_app.model.Payment;
import com.etoro.payment_gateway_app.model.PaymentStatus;
import com.etoro.payment_gateway_app.repository.IdempotencyRepository;
import com.etoro.payment_gateway_app.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentService {

    private final IdempotencyRepository idempotencyRepository;
    private final PaymentRepository paymentRepository;
    private final BankClient bankClient;
    private final ObjectMapper objectMapper;

    public PaymentService(
            IdempotencyRepository idempotencyRepository,
            PaymentRepository paymentRepository,
            BankClient bankClient,
            ObjectMapper objectMapper
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.paymentRepository = paymentRepository;
        this.bankClient = bankClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse authorize(AuthorizePaymentRequest request, String idempotencyKey) {
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if(existingKey.isPresent()){

            PaymentResponse response;

            try {
               response = objectMapper.readValue(
                        existingKey.get().getResponseBody(),
                        PaymentResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException("Couldnt process checks", e);
            }

            return response;
        }

        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setCustomerId(request.getCustomerId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCardNumber(request.getCardDetails().getCardNumber());
        payment.setCardExpiry(request.getCardDetails().getExpiryDate());
        payment.setCardCvv(request.getCardDetails().getCvv());
        payment.setStatus(PaymentStatus.PENDING);

        paymentRepository.save(payment);

        AuthorizeRequest authorizeRequest = new AuthorizeRequest(
                request.getAmount(),
                request.getCardDetails()
        );

        AuthorizationResponse bankResponse = bankClient.authorize(authorizeRequest, idempotencyKey);

        payment.setBankAuthId(bankResponse.getAuthorizationId());
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAt(LocalDateTime.now());

        IdempotencyKey key = new IdempotencyKey();

        PaymentResponse paymentResponse = new PaymentResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getOrderId(),
                payment.getCustomerId()
        );

        key.setIdempotencyKey(idempotencyKey);
        key.setPaymentId(payment.getId());
        key.setOperation("AUTHORIZE");

        try {
            String responseBody = objectMapper.writeValueAsString(paymentResponse);
            key.setResponseBody(responseBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize payment response", e);
        }

        idempotencyRepository.save(key);

        return paymentResponse;
    }
}
