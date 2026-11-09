package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.user.Role;
import com.antoniosifov.popcornpalace.user.User;
import com.antoniosifov.popcornpalace.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService
{
    /** BCrypt only looks at the first 72 bytes of a password. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;

    /** A real hash of a password nobody knows; see {@link #login}. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService, Clock clock)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request)
    {
        // The size limit on the request counts characters; non-ASCII ones take several bytes.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "password must be at most %d bytes when encoded as UTF-8".formatted(MAX_PASSWORD_BYTES));
        }
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        User user = userRepository.saveAndFlush(new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                Role.CUSTOMER,
                clock.instant().truncatedTo(ChronoUnit.MILLIS)));
        return AuthResponse.of(tokenService.issue(user.getId(), user.getRole()), user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request)
    {
        Optional<User> user = userRepository.findByEmail(request.email());

        // The password is checked against a hash even when the email is unknown.
        // Otherwise an unknown email would answer noticeably faster than a wrong
        // password, and an attacker could use that to find out which emails have accounts.
        String passwordHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

        if (user.isEmpty() || !passwordMatches) {
            // One message for both cases, for the same reason.
            throw ApiException.unauthorized(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
        }
        return AuthResponse.of(tokenService.issue(user.get().getId(), user.get().getRole()), user.get());
    }
}
