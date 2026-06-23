CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(255) NOT NULL,
    customer_id     VARCHAR(255) NOT NULL,
    amount          BIGINT NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    status          VARCHAR(50) NOT NULL,

    -- card details (store masked in production, fine for now)
    card_number     VARCHAR(19),
    card_expiry     VARCHAR(7),
    card_cvv        VARCHAR(4),

    -- bank-assigned IDs (filled in as operations complete)
    bank_auth_id    VARCHAR(255),
    bank_capture_id VARCHAR(255),
    bank_void_id    VARCHAR(255),
    bank_refund_id  VARCHAR(255),

    -- timestamps for each state transition
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    authorized_at   TIMESTAMP,
    captured_at     TIMESTAMP,
    voided_at       TIMESTAMP,
    refunded_at     TIMESTAMP
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    payment_id      UUID NOT NULL REFERENCES payments(id),
    operation       VARCHAR(50) NOT NULL,
    response_body   TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   UUID NOT NULL REFERENCES payments(id),
    from_status  VARCHAR(50),
    to_status    VARCHAR(50) NOT NULL,
    occurred_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- indexes for the queries FicMart will make
CREATE INDEX idx_payments_order_id    ON payments(order_id);
CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payment_events_payment_id ON payment_events(payment_id);