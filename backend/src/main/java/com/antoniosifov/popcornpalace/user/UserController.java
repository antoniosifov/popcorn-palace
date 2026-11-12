package com.antoniosifov.popcornpalace.user;

import com.antoniosifov.popcornpalace.auth.AuthRateLimitProperties;
import com.antoniosifov.popcornpalace.common.config.OpenApiConfig;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController
{
    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletion;
    private final RateLimiter rateLimiter;
    private final AuthRateLimitProperties rateLimit;

    public UserController(UserRepository userRepository, AccountDeletionService accountDeletion,
                          RateLimiter rateLimiter, AuthRateLimitProperties rateLimit)
    {
        this.userRepository = userRepository;
        this.accountDeletion = accountDeletion;
        this.rateLimiter = rateLimiter;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/me")
    @Operation(summary = "The account that owns the access token")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt)
    {
        UUID userId = UUID.fromString(jwt.getSubject());
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .map(UserResponse::from)
                // The token is still valid, but the account behind it is gone.
                .orElseThrow(() -> ApiException.unauthorized(ErrorCode.UNAUTHENTICATED, "The account no longer exists"));
    }

    /**
     * A POST and not a DELETE on purpose. The password has to travel with the
     * request and must not be in the address, and a body on a DELETE is
     * something proxies and clients are free to drop.
     */
    @PostMapping("/me/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the account that owns the access token", description = """
            For good. Bookings for showtimes that have not begun are cancelled and refunded, and their seats \
            go back on sale. The email, name, password and Google connection are erased, every access token \
            of the account stops working, and one last email says so. What remains are bookings and payments \
            that belong to nobody. An account that has a password must send it: 400 PASSWORD_INCORRECT \
            otherwise; one opened with Google sends nothing. An administrator account is refused with 409 \
            ACCOUNT_NOT_DELETABLE. Counted against the same limit as login attempts.""")
    public void deleteMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody(required = false) DeleteAccountRequest request,
                         HttpServletRequest httpRequest)
    {
        // The same budget as logging in: this, too, answers whether a password is right.
        rateLimiter.check("auth:" + httpRequest.getRemoteAddr(), rateLimit.maxRequests(), rateLimit.window());
        accountDeletion.delete(UUID.fromString(jwt.getSubject()), request == null ? null : request.password());
    }
}
