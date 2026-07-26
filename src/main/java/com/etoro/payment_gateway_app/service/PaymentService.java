package com.etoro.payment_gateway_app.service;


import com.etoro.payment_gateway_app.client.BankClient;
import com.etoro.payment_gateway_app.dto.*;
import com.etoro.payment_gateway_app.exceptions.InvalidStateTransitionException;
import com.etoro.payment_gateway_app.exceptions.PaymentNotFoundException;
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
import java.util.UUID;

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

        // Check idempotency key
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if(existingKey.isPresent()){

            PaymentResponse response;

            try {
               response = objectMapper.readValue(
                        existingKey.get().getResponseBody(),
                        PaymentResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException("Couldn't process checks", e);
            }

            return response;
        }

        // Create payment, if key doesn't exist
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setCustomerId(request.getCustomerId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCardNumber(request.getCardDetails().getCardNumber());
        payment.setCardExpiry(request.getCardDetails().getExpiryDate());
        payment.setCardCvv(request.getCardDetails().getCvv());
        payment.setStatus(PaymentStatus.PENDING);


        // Save the payment
        paymentRepository.save(payment);

        // Calling the bank, using AuthorizeRequest DTO
        AuthorizeRequest authorizeRequest = new AuthorizeRequest(
                request.getAmount(),
                request.getCardDetails()
        );

        AuthorizationResponse bankResponse = bankClient.authorize(authorizeRequest, idempotencyKey);

        payment.setBankAuthId(bankResponse.getAuthorizationId());
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAt(LocalDateTime.now());

        IdempotencyKey key = new IdempotencyKey();

        // Getting paymentResponse
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


    @Transactional
    public PaymentResponse capture(UUID paymentReference, String idempotencyKey) {

        // Check idempotency key
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if(existingKey.isPresent()) {

            PaymentResponse response;

            try {
                response = objectMapper.readValue(
                        existingKey.get().getResponseBody(),
                        PaymentResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException("Couldn't process response", e);
            }

            return response;
        }

        // Find the payment
        Payment payment = paymentRepository.findById(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException(paymentReference));


        // Validation for capturing
        if(!payment.canTransitionTo(PaymentStatus.CAPTURED)) {
            throw new InvalidStateTransitionException(
                    "Cannot capture payment in state " + payment.getStatus()
            );
        }

        // Call the Bank
        CaptureResponse bankResponse = bankClient.capture(payment.getBankAuthId(), idempotencyKey);

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(LocalDateTime.now());
        payment.setBankCaptureId(bankResponse.getCaptureId());

        // Create paymentResponse
        PaymentResponse paymentResponse =
                new PaymentResponse(
                        payment.getId(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getOrderId(),
                        payment.getCustomerId()
                );

        // Save Idempotency record
        IdempotencyKey key = new IdempotencyKey();
        key.setIdempotencyKey(idempotencyKey);
        key.setPaymentId(payment.getId());
        key.setOperation("CAPTURE");

        try {
            String responseBody = objectMapper.writeValueAsString(paymentResponse);
            key.setResponseBody(responseBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Couldn't get response", e);
        }
        idempotencyRepository.save(key);

        return paymentResponse;

    }


    @Transactional
    public PaymentResponse voidAuthorization(UUID paymentReference, String idempotencyKey) {

        // Check for idempotencyKey
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if(existingKey.isPresent()) {
            PaymentResponse response;

            try {
                response = objectMapper.readValue(
                        existingKey.get().getResponseBody(),
                        PaymentResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException("Couldn't get response", e);
            }

            return response;
        }

        Payment payment = paymentRepository.findById(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException(paymentReference));

        // Validation for voiding
        if(!payment.canTransitionTo(PaymentStatus.VOIDED)) {
            throw new InvalidStateTransitionException(
                    "Cannot void payment in state " + payment.getStatus()
            );
        }

        VoidResponse bankResponse = bankClient.voidAuthorization(payment.getBankAuthId(), idempotencyKey);

        payment.setStatus(PaymentStatus.VOIDED);
        payment.setVoidedAt(LocalDateTime.now());
        payment.setBankVoidId(bankResponse.getVoidId());

        // Create paymentResponse
        PaymentResponse paymentResponse =
                new PaymentResponse(
                        payment.getId(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getOrderId(),
                        payment.getCustomerId()
                );

        IdempotencyKey key = new IdempotencyKey();
        key.setIdempotencyKey(idempotencyKey);
        key.setPaymentId(payment.getId());
        key.setOperation("VOID");

        try {
            String responseBody = objectMapper.writeValueAsString(paymentResponse);
            key.setResponseBody(responseBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Couldn't get response", e);
        }
        idempotencyRepository.save(key);

        return paymentResponse;
    }

    @Transactional
    public PaymentResponse refund(UUID paymentReference, String idempotencyKey) {

        // Check for idempotencyKey
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if(existingKey.isPresent()) {
            PaymentResponse response;

            try {
                response = objectMapper.readValue(
                        existingKey.get().getResponseBody(),
                        PaymentResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException("Couldn't get response", e);
            }

            return response;
        }

        Payment payment = paymentRepository.findById(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException(paymentReference));

        // Validation for refund
        if(!payment.canTransitionTo(PaymentStatus.REFUNDED)) {
            throw new InvalidStateTransitionException(
                    "Cannot refund payment in state " + payment.getStatus()
            );
        }

        RefundResponse bankResponse = bankClient.refund(payment.getBankCaptureId(), idempotencyKey);

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setBankRefundId(bankResponse.getRefundId());

        // Create paymentResponse
        PaymentResponse paymentResponse =
                new PaymentResponse(
                        payment.getId(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getOrderId(),
                        payment.getCustomerId()
                );

        IdempotencyKey key = new IdempotencyKey();
        key.setIdempotencyKey(idempotencyKey);
        key.setPaymentId(payment.getId());
        key.setOperation("REFUND");

        try {
            String responseBody = objectMapper.writeValueAsString(paymentResponse);
            key.setResponseBody(responseBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Couldn't get response", e);
        }
        idempotencyRepository.save(key);

        return paymentResponse;
    }
}
