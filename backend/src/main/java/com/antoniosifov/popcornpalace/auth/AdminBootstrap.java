package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.security.SecurityProperties;
import com.antoniosifov.popcornpalace.user.Role;
import com.antoniosifov.popcornpalace.user.User;
import com.antoniosifov.popcornpalace.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Registration only ever creates customers, so the first administrator has to
 * come from somewhere else. When ADMIN_EMAIL and ADMIN_PASSWORD are set, this
 * creates that administrator at startup if the account does not exist yet.
 * An existing account is left untouched: restarting never resets a password.
 */
@Component
public class AdminBootstrap implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecurityProperties.Admin admin;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          Clock clock, SecurityProperties properties)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.admin = properties.admin();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args)
    {
        if (admin == null || isBlank(admin.email()) || isBlank(admin.password())) {
            return;
        }
        if (admin.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least %d characters long".formatted(MIN_PASSWORD_LENGTH));
        }
        String email = User.normalizeEmail(admin.email());
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(admin.password()), "Administrator",
                Role.ADMIN, clock.instant().truncatedTo(ChronoUnit.MILLIS)));
        log.info("Created administrator account {}", email);
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }
}
