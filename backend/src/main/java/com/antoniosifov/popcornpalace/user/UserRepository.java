package com.antoniosifov.popcornpalace.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>
{
    /** @param email already normalized with {@link User#normalizeEmail} */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    /**
     * The account, held until the transaction ends so that it cannot be deleted
     * meanwhile. For whoever is about to attach something to it: a booking
     * saved while its account was being deleted would belong to nobody.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdKeepingIt(@Param("id") UUID id);

    /**
     * The account, for deleting it: waits for every transaction that is
     * {@linkplain #findByIdKeepingIt keeping it}, and makes the ones that come after wait in turn.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForDeletion(@Param("id") UUID id);
}
