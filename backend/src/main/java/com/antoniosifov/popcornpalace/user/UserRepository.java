package com.antoniosifov.popcornpalace.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>
{
    /** @param email already normalized with {@link User#normalizeEmail} */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
