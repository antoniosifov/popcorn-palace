-- Payments, and bookings that can be cancelled.

-- ---------------------------------------------------------------- payments

-- One row per attempt to pay for a hold. The row is created before the
-- payment provider is called, so a crash in the middle leaves a trace that
-- the reconciliation job can pick up.
CREATE TABLE payments
(
    id              UUID PRIMARY KEY,
    user_id         UUID           NOT NULL,
    -- What was being paid for. Plain values, not foreign keys: a payment
    -- attempt is a historical fact and must not block deleting a showtime.
    showtime_id     BIGINT         NOT NULL,
    hold_id         UUID           NOT NULL,
    idempotency_key VARCHAR(100)   NOT NULL,
    amount          NUMERIC(10, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    -- Why the payment failed or was refunded, for example a decline code.
    failure_code    VARCHAR(50),
    -- Incremented on every update; lets the application detect two requests
    -- changing the same payment at once (optimistic locking).
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- The idempotency guarantee: a client that repeats a request with the same
    -- key cannot create a second payment, even if both arrive at the same time.
    CONSTRAINT uq_payments_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_currency CHECK (char_length(currency) = 3),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUND_PENDING', 'REFUNDED'))
);

-- The reconciliation job looks only for unfinished payments, which are a
-- tiny fraction of the table; a partial index keeps that lookup small.
CREATE INDEX idx_payments_unfinished ON payments (status, updated_at)
    WHERE status IN ('PENDING', 'REFUND_PENDING');

-- ---------------------------------------------------------------- bookings

ALTER TABLE bookings
    -- Null for bookings made before payments existed.
    ADD COLUMN payment_id   UUID,
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN version      BIGINT      NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_bookings_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    ADD CONSTRAINT ck_bookings_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    ADD CONSTRAINT ck_bookings_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL));

-- A payment pays for exactly one booking.
CREATE UNIQUE INDEX uq_bookings_payment ON bookings (payment_id) WHERE payment_id IS NOT NULL;

-- ---------------------------------------------------------------- seats of cancelled bookings

-- A cancelled booking keeps its seat rows (they are its history) but must stop
-- blocking the seats. "One seat is sold once per showtime" therefore becomes
-- "at most one ACTIVE sale per seat per showtime", which a partial unique
-- index expresses directly. It keeps the old name, so violations are reported
-- the same way as before.
ALTER TABLE booking_seats
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE booking_seats
    DROP CONSTRAINT uq_booking_seats_showtime_seat;

CREATE UNIQUE INDEX uq_booking_seats_showtime_seat ON booking_seats (showtime_id, seat_id) WHERE active;
