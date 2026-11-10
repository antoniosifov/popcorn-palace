-- The outbox row remembers which trace the booking request belonged to (its
-- W3C "traceparent" header). The relay sends the Kafka message later, on
-- another thread and perhaps on another instance; with this it still sends it
-- as part of that trace, so the request, the message and the email it caused
-- can be looked at as one.
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(100);
