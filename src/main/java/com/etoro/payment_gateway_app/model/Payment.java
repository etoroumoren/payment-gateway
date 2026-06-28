package com.etoro.payment_gateway_app.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "card_expiry")
    private String cardExpiry;

    @Column(name = "card_cvv")
    private String cardCvv;

    @Column(name = "bank_auth_id")
    private String bankAuthId;

    @Column(name = "bank_capture_id")
    private String bankCaptureId;

    @Column(name = "bank_void_id")
    private String bankVoidId;

    @Column(name = "bank_refund_id")
    private String bankRefundId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean canTransitionTo (PaymentStatus newStatus) {
        return switch (this.status) {
            case PENDING -> newStatus == PaymentStatus.AUTHORIZED || newStatus == PaymentStatus.FAILED;
            case AUTHORIZED -> newStatus == PaymentStatus.CAPTURED || newStatus == PaymentStatus.VOIDED;
            case CAPTURED -> newStatus == PaymentStatus.REFUNDED;
            default -> false;
        };
    }
}
