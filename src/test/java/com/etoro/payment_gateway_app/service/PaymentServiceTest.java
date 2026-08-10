package com.etoro.payment_gateway_app.service;


import com.etoro.payment_gateway_app.client.BankClient;
import com.etoro.payment_gateway_app.dto.*;
import com.etoro.payment_gateway_app.exceptions.InvalidStateTransitionException;
import com.etoro.payment_gateway_app.exceptions.PaymentNotFoundException;
import com.etoro.payment_gateway_app.model.Payment;
import com.etoro.payment_gateway_app.model.PaymentStatus;
import com.etoro.payment_gateway_app.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BankClient bankClient;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void returnCachedResponseIfIdempotencyKeyExists() {
        String idempotencyKey = "abc-111";
        UUID paymentId = UUID.randomUUID();

        PaymentResponse cachedResponse = new PaymentResponse(
                paymentId,
                PaymentStatus.AUTHORIZED,
                500L,
                "ORDER-123",
                "CUSTOMER-123"
        );

        when(idempotencyService.findCachedResponse(
                idempotencyKey, PaymentResponse.class))
                .thenReturn(Optional.of(cachedResponse));

        CardDetails cardDetails = new CardDetails(
                "11111111111",
                "12/28",
                "123"
        );

        AuthorizePaymentRequest request = new AuthorizePaymentRequest(
                5000L,
                cardDetails,
                "sis",
                "CUSTOMER-123",
                "$"
        );

        PaymentResponse result = paymentService.authorize(request, idempotencyKey);

        assertEquals(cachedResponse, result);

        verify(bankClient, never()).authorize(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void authorizePaymentIfIdempotencyKeyDoesNotExist() {
        String idempotencyKey = "abc-222";

        AuthorizePaymentRequest request = createAuthorizeRequest();

        when(idempotencyService.findCachedResponse(
                idempotencyKey,
                PaymentResponse.class
        )).thenReturn(Optional.empty());

        Payment payment = new Payment();

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment savedPayment = invocation.getArgument(0);
                    savedPayment.setId(UUID.randomUUID());
                    return savedPayment;
                });

        AuthorizationResponse bankResponse = new AuthorizationResponse(
                "authorization",
                "authorized",
                5000L
        );

        when(bankClient.authorize(any(), eq(idempotencyKey)))
                .thenReturn(bankResponse);

        PaymentResponse result = paymentService.authorize(request, idempotencyKey);

        assertEquals(PaymentStatus.AUTHORIZED, result.getStatus());
        assertEquals(5000L, result.getAmount());
        assertEquals("ORDER-123", result.getOrderId());
        assertEquals("CUSTOMER-123", result.getCustomerId());

        verify(paymentRepository).save(any(Payment.class));
        verify(bankClient).authorize(any(), eq(idempotencyKey));
        verify(idempotencyService).saveKey(
                eq(idempotencyKey),
                any(UUID.class),
                eq("AUTHORIZE"),
                any(PaymentResponse.class)
        );
    }

    @Test
    void captureAuthorizedPayment() {
        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "capture-111";

        Payment payment = createPayment(paymentId, PaymentStatus.AUTHORIZED );
        payment.setBankAuthId("AUTH-123");

        when(idempotencyService.findCachedResponse(
                idempotencyKey, PaymentResponse.class
        )).thenReturn(Optional.empty());

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        CaptureResponse bankResponse = new CaptureResponse("CAPTURE-123");

        when(bankClient.capture(
                "AUTH-123", idempotencyKey, 5000L
        )).thenReturn(bankResponse);

        PaymentResponse result = paymentService.capture(paymentId, idempotencyKey);

        assertEquals(PaymentStatus.CAPTURED, result.getStatus());
        assertEquals(paymentId, result.getPaymentReference());

        verify(bankClient).capture( "AUTH-123", idempotencyKey, 5000L);

        verify(idempotencyService).saveKey(
                eq(idempotencyKey),
                eq(paymentId),
                eq("CAPTURE"),
                any(PaymentResponse.class));

    }

    @Test
    void returnCachedResponseInsteadOfCapturingAgain() {
        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "capture-222";

        PaymentResponse cachedResponse = new PaymentResponse(
                paymentId,
                PaymentStatus.CAPTURED,
                5000L,
                "ORDER-123",
                "CUSTOMER-123"

        );

        when(idempotencyService.findCachedResponse(
                idempotencyKey, PaymentResponse.class
        )).thenReturn(Optional.of(cachedResponse));

        PaymentResponse result = paymentService.capture(paymentId, idempotencyKey);

        assertEquals(cachedResponse, result);

        verify(paymentRepository, never()).findById(any());
        verify(bankClient, never()).capture(anyString(), anyString(), anyLong());
    }

    @Test
    void throwPaymentNotFoundWhenCapturingUnknownPayment() {

        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "capture-333";

        when(idempotencyService.findCachedResponse(
                idempotencyKey, PaymentResponse.class
        )).thenReturn(Optional.empty());

        when(paymentRepository.findById(
                paymentId
        )).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> paymentService.capture(
                paymentId, idempotencyKey
        ));

        verify(bankClient, never()).capture(anyString(), anyString(), anyLong());
    }

    @Test
    void rejectCaptureWhenPaymentIsInInvalidState() {
        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "capture-444";

        Payment payment = createPayment(paymentId, PaymentStatus.PENDING );

        when(idempotencyService.findCachedResponse(
                idempotencyKey, PaymentResponse.class
        )).thenReturn(Optional.empty());

        when(paymentRepository.findById(
                paymentId
        )).thenReturn(Optional.of(payment));

        assertThrows(InvalidStateTransitionException.class, () -> paymentService.capture(
                paymentId, idempotencyKey
        ));

        verify(bankClient, never()).capture(anyString(), anyString(), anyLong());
    }

    @Test
    void voidAuthorizedPayment() {

        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "void-111";

        Payment payment = createPayment(paymentId, PaymentStatus.AUTHORIZED);
        payment.setBankAuthId("AUTH-123");

        when(idempotencyService.findCachedResponse(idempotencyKey, PaymentResponse.class))
                .thenReturn(Optional.empty());

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        VoidResponse bankResponse = new VoidResponse("VOID-123");
        when(bankClient.voidAuthorization("AUTH-123", idempotencyKey))
                .thenReturn(bankResponse);


        PaymentResponse result = paymentService.voidAuthorization(paymentId, idempotencyKey);

        assertEquals(PaymentStatus.VOIDED, result.getStatus());
        assertEquals(paymentId, result.getPaymentReference());

        verify(bankClient).voidAuthorization("AUTH-123", idempotencyKey);
        verify(idempotencyService).saveKey(
                eq(idempotencyKey),
                eq(paymentId),
                eq("VOID"),
                any(PaymentResponse.class)
        );
    }

    @Test
    void rejectVoidWhenPaymentIsInInvalidState() {

        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "void-222";

        Payment payment = createPayment(paymentId, PaymentStatus.CAPTURED);

        when(idempotencyService.findCachedResponse(idempotencyKey, PaymentResponse.class))
                .thenReturn(Optional.empty());

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(InvalidStateTransitionException.class,
                () -> paymentService.voidAuthorization(paymentId, idempotencyKey));

        verify(bankClient, never()).voidAuthorization(anyString(), anyString());
    }

// ============================================================
// REFUND
// ============================================================

    @Test
    void refundCapturedPayment() {

        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "refund-111";

        Payment payment = createPayment(paymentId, PaymentStatus.CAPTURED);
        payment.setBankCaptureId("CAPTURE-123");

        when(idempotencyService.findCachedResponse(idempotencyKey, PaymentResponse.class))
                .thenReturn(Optional.empty());

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        RefundResponse bankResponse = new RefundResponse("REFUND-123");
        when(bankClient.refund("CAPTURE-123", idempotencyKey, payment.getAmount()))
                .thenReturn(bankResponse);


        PaymentResponse result = paymentService.refund(paymentId, idempotencyKey);


        assertEquals(PaymentStatus.REFUNDED, result.getStatus());
        assertEquals(paymentId, result.getPaymentReference());

        verify(bankClient).refund("CAPTURE-123", idempotencyKey, payment.getAmount());
        verify(idempotencyService).saveKey(
                eq(idempotencyKey),
                eq(paymentId),
                eq("REFUND"),
                any(PaymentResponse.class)
        );
    }

    @Test
    void rejectRefundWhenPaymentIsInInvalidState() {

        UUID paymentId = UUID.randomUUID();
        String idempotencyKey = "refund-222";

        Payment payment = createPayment(paymentId, PaymentStatus.AUTHORIZED);

        when(idempotencyService.findCachedResponse(idempotencyKey, PaymentResponse.class))
                .thenReturn(Optional.empty());

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));


        assertThrows(InvalidStateTransitionException.class,
                () -> paymentService.refund(paymentId, idempotencyKey));

        verify(bankClient, never()).refund(anyString(), anyString(), anyLong());
    }

// ============================================================
// QUERY METHODS
// ============================================================

    @Test
    void getPaymentById() {

        UUID paymentId = UUID.randomUUID();
        Payment payment = createPayment(paymentId, PaymentStatus.AUTHORIZED);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));


        PaymentResponse result = paymentService.getPayment(paymentId);


        assertEquals(paymentId, result.getPaymentReference());
        assertEquals(PaymentStatus.AUTHORIZED, result.getStatus());
        assertEquals(payment.getAmount(), result.getAmount());
        assertEquals(payment.getOrderId(), result.getOrderId());
        assertEquals(payment.getCustomerId(), result.getCustomerId());
    }

    @Test
    void throwPaymentNotFoundWhenGettingUnknownPayment() {

        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.empty());


        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.getPayment(paymentId));
    }

    @Test
    void getPaymentByOrderId() {

        UUID paymentId = UUID.randomUUID();
        String orderId = "ORDER-123";

        Payment payment = createPayment(paymentId, PaymentStatus.AUTHORIZED);

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(payment));


        PaymentResponse result = paymentService.getPaymentByOrderId(orderId);

        assertEquals(paymentId, result.getPaymentReference());
        assertEquals(orderId, result.getOrderId());
        assertEquals(PaymentStatus.AUTHORIZED, result.getStatus());
    }

    @Test
    void throwPaymentNotFoundWhenGettingUnknownOrder() {

        String orderId = "UNKNOWN-ORDER";

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.getPaymentByOrderId(orderId));
    }

// ============================================================
// TEST DATA HELPERS
// ============================================================

    private AuthorizePaymentRequest createAuthorizeRequest() {
        CardDetails cardDetails = new CardDetails(
                "4111111111111111",
                "12/28",
                "123"
        );

        return new AuthorizePaymentRequest(
                5000L,
                cardDetails,
                "ORDER-123",
                "CUSTOMER-123",
                "NGN"
        );
    }

    private Payment createPayment(UUID paymentId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setAmount(5000L);
        payment.setOrderId("ORDER-123");
        payment.setCustomerId("CUSTOMER-123");
        payment.setCurrency("NGN");
        payment.setStatus(status);
        return payment;
    }
}
