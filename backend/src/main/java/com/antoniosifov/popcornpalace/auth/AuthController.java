package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.common.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Create an account and obtain an access token")
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthController
{
    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final AuthRateLimitProperties rateLimit;

    public AuthController(AuthService authService, RateLimiter rateLimiter, AuthRateLimitProperties rateLimit)
    {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer account", description = "Returns an access token, so the new user is logged in right away.")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest)
    {
        throttle(httpRequest);
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = """
            Fails with 401 INVALID_CREDENTIALS for an unknown email and for a wrong password alike, \
            and with 429 after too many attempts from one address.""")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest)
    {
        throttle(httpRequest);
        return authService.login(request);
    }

    /**
     * Slows down password guessing and mass sign-ups. Counted per client
     * address across both endpoints, before any password hashing happens.
     */
    private void throttle(HttpServletRequest httpRequest)
    {
        rateLimiter.check("auth:" + httpRequest.getRemoteAddr(), rateLimit.maxRequests(), rateLimit.window());
    }
}
