package com.antoniosifov.popcornpalace.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>
{
    Optional<Payment> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** Unfinished payments that nobody has touched since the given moment; work for the reconciliation job. */
    List<Payment> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(PaymentStatus status, Instant updatedBefore);
}
