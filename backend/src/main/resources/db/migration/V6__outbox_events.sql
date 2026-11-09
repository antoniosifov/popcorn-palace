-- Transactional outbox.
--
-- When a booking is confirmed or cancelled, other systems need to hear about
-- it (tickets by email, for one). Sending a message to Kafka cannot be part
-- of a database transaction, so doing both leaves two bad outcomes: the
-- booking is saved but the message is lost, or the message is sent but the
-- booking rolls back.
--
-- Instead, the event is written to this table in the SAME transaction as the
-- booking. Both are saved or neither is. A background relay then reads rows
-- that are not published yet, sends them to Kafka and marks them published.
-- If it crashes between sending and marking, the row is sent again, so
-- delivery is at-least-once and consumers deduplicate by event_id.

CREATE TABLE outbox_events
(
    -- Events are sent in the order they were written.
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Identifies the event to consumers, across retries.
    event_id     UUID         NOT NULL,
    topic        VARCHAR(200) NOT NULL,
    -- Kafka keeps messages with the same key in order; the booking id is used,
    -- so "confirmed" always reaches a consumer before "cancelled".
    message_key  VARCHAR(200) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    -- JSON, not JSONB: JSONB would re-order keys and re-format numbers, and what
    -- is published must be byte for byte what the application wrote.
    payload      JSON         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_outbox_events_event_id UNIQUE (event_id)
);

-- The relay only ever asks for unpublished rows, normally a handful.
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (id) WHERE published_at IS NULL;
