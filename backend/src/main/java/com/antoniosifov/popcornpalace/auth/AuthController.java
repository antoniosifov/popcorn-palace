package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.auth.google.GoogleProperties;
import com.antoniosifov.popcornpalace.auth.google.GoogleSignInRequest;
import com.antoniosifov.popcornpalace.auth.google.GoogleSignInService;
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
@Tag(name = "Authentication", description = "Create an account, obtain an access token, replace a forgotten password")
@EnableConfigurationProperties({AuthRateLimitProperties.class, PasswordResetProperties.class, GoogleProperties.class})
public class AuthController
{
    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final GoogleSignInService googleSignInService;
    private final RateLimiter rateLimiter;
    private final AuthRateLimitProperties rateLimit;

    public AuthController(AuthService authService, PasswordResetService passwordResetService,
                          GoogleSignInService googleSignInService,
                          RateLimiter rateLimiter, AuthRateLimitProperties rateLimit)
    {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.googleSignInService = googleSignInService;
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

    @PostMapping("/google")
    @Operation(summary = "Sign in with Google", description = """
            Takes the ID token Google issued when the person signed in, checks it (signature, issuer, \
            that it was issued for this application, expiry, nonce) and answers like a login. The first \
            sign-in opens an account; later ones find it by Google's subject, not by email. An existing \
            account with the same email is connected, and loses its password and sessions if its email \
            had never been proven. Fails with 401 GOOGLE_SIGN_IN_FAILED for a token that fails any check \
            and with 404 GOOGLE_SIGN_IN_DISABLED where no Google client id is configured.""")
    public AuthResponse signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request, HttpServletRequest httpRequest)
    {
        throttle(httpRequest);
        return googleSignInService.signIn(request.idToken(), request.nonce());
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Ask for a link to set a new password", description = """
            Always answers 202, whether or not the email has an account, so the endpoint cannot be used \
            to find out which emails are registered. If it has one, an email with a link is sent; the link \
            works once, for 30 minutes, and replaces any link sent before it. One account is sent at most \
            three such emails an hour.""")
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest)
    {
        throttle(httpRequest);
        passwordResetService.request(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password with the token from the email", description = """
            Fails with 400 RESET_LINK_INVALID for a token that is unknown, expired, replaced by a newer one \
            or already used. On success every access token issued to the account before stops working.""")
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest httpRequest)
    {
        throttle(httpRequest);
        passwordResetService.confirm(request.token(), request.password());
    }

    /**
     * Slows down password guessing, mass sign-ups and guessing of reset tokens.
     * Counted per client address across all five endpoints, before any password
     * hashing happens.
     */
    private void throttle(HttpServletRequest httpRequest)
    {
        rateLimiter.check("auth:" + httpRequest.getRemoteAddr(), rateLimit.maxRequests(), rateLimit.window());
    }
}
