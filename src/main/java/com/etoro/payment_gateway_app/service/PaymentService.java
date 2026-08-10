package com.etoro.payment_gateway_app.service;


import com.etoro.payment_gateway_app.client.BankClient;
import com.etoro.payment_gateway_app.dto.*;
import com.etoro.payment_gateway_app.exceptions.InvalidStateTransitionException;
import com.etoro.payment_gateway_app.exceptions.PaymentNotFoundException;
import com.etoro.payment_gateway_app.model.Payment;
import com.etoro.payment_gateway_app.model.PaymentStatus;
import com.etoro.payment_gateway_app.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final BankClient bankClient;
    private final IdempotencyService idempotencyService;
    private final PaymentRepository paymentRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            BankClient bankClient,
            IdempotencyService idempotencyService
    ) {
        this.paymentRepository = paymentRepository;
        this.bankClient = bankClient;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public PaymentResponse authorize(AuthorizePaymentRequest request, String idempotencyKey) {

        Optional<PaymentResponse> cached =
                idempotencyService.findCachedResponse(
                        idempotencyKey,
                        PaymentResponse.class
                );

        if (cached.isPresent()) {
            return cached.get();
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
        String[] expiry = request.getCardDetails().getExpiryDate().split("/");
        AuthorizeRequest authorizeRequest = new AuthorizeRequest(
                request.getAmount(),
                request.getCardDetails().getCardNumber(),
                request.getCardDetails().getCvv(),
                Integer.parseInt(expiry[0]),
                Integer.parseInt(expiry[1])
        );

        AuthorizationResponse bankResponse = bankClient.authorize(authorizeRequest, idempotencyKey);

        payment.setBankAuthId(bankResponse.getAuthorizationId());
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAt(LocalDateTime.now());

        // Getting paymentResponse
        PaymentResponse paymentResponse = new PaymentResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getOrderId(),
                payment.getCustomerId()
        );

        idempotencyService.saveKey(
                idempotencyKey,
                payment.getId(),
                "AUTHORIZE",
                paymentResponse
        );

        return paymentResponse;
    }


    @Transactional
    public PaymentResponse capture(UUID paymentReference, String idempotencyKey) {

        Optional<PaymentResponse> cached =
                idempotencyService.findCachedResponse(
                        idempotencyKey,
                        PaymentResponse.class
                );

        if (cached.isPresent()) {
            return cached.get();
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
        CaptureResponse bankResponse = bankClient.capture(payment.getBankAuthId(), idempotencyKey, payment.getAmount());

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
        idempotencyService.saveKey(
                idempotencyKey,
                payment.getId(),
                "CAPTURE",
                paymentResponse
        );

        return paymentResponse;

    }


    @Transactional
    public PaymentResponse voidAuthorization(UUID paymentReference, String idempotencyKey) {

        Optional<PaymentResponse> cached =
                idempotencyService.findCachedResponse(
                        idempotencyKey,
                        PaymentResponse.class
                );

        if (cached.isPresent()) {
            return cached.get();
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

        idempotencyService.saveKey(
                idempotencyKey,
                payment.getId(),
                "VOID",
                paymentResponse
        );

        return paymentResponse;
    }

    @Transactional
    public PaymentResponse refund(UUID paymentReference, String idempotencyKey) {

        Optional<PaymentResponse> cached =
                idempotencyService.findCachedResponse(
                        idempotencyKey,
                        PaymentResponse.class
                );

        if (cached.isPresent()) {
            return cached.get();
        }

        Payment payment = paymentRepository.findById(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException(paymentReference));

        // Validation for refund
        if(!payment.canTransitionTo(PaymentStatus.REFUNDED)) {
            throw new InvalidStateTransitionException(
                    "Cannot refund payment in state " + payment.getStatus()
            );
        }

        RefundResponse bankResponse = bankClient.refund(payment.getBankCaptureId(), idempotencyKey, payment.getAmount());

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

        idempotencyService.saveKey(
                idempotencyKey,
                payment.getId(),
                "REFUND",
                paymentResponse
        );

        return paymentResponse;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        PaymentResponse paymentResponse =
                new PaymentResponse(
                        payment.getId(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getOrderId(),
                        payment.getCustomerId()
                );

        return paymentResponse;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(String orderId) {

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new PaymentNotFoundException("No payment found for order: " + orderId));

        PaymentResponse paymentResponse =
                new PaymentResponse(
                        payment.getId(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getOrderId(),
                        payment.getCustomerId()
                );

        return paymentResponse;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentByCustomerId(String customerId) {
        List<Payment> payments = paymentRepository.findByCustomerId(customerId);

        return payments.stream()
                .map(payment -> new PaymentResponse(
                        payment.getId(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getOrderId(),
                        payment.getCustomerId()
                ))
                .toList();
    }
}
