package com.etoro.payment_gateway_app.controller;


import com.etoro.payment_gateway_app.dto.AuthorizePaymentRequest;
import com.etoro.payment_gateway_app.dto.PaymentResponse;
import com.etoro.payment_gateway_app.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponse> authorize(@Valid @RequestBody AuthorizePaymentRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentResponse response = paymentService.authorize(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<PaymentResponse> capture(@PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentResponse response = paymentService.capture(paymentId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/void")
    public ResponseEntity<PaymentResponse> voidAuthorization( @PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentResponse response = paymentService.voidAuthorization(paymentId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentResponse response = paymentService.refund(paymentId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId) {

        PaymentResponse response = paymentService.getPayment(paymentId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @PathVariable String orderId) {

        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByCustomerId(@PathVariable String customerId) {
        List<PaymentResponse> responses = paymentService.getPaymentByCustomerId(customerId);

        return ResponseEntity.ok(responses);
    }
}
