package com.antoniosifov.popcornpalace.user;

import com.antoniosifov.popcornpalace.common.config.OpenApiConfig;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController
{
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository)
    {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "The account that owns the access token")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt)
    {
        UUID userId = UUID.fromString(jwt.getSubject());
        return userRepository.findById(userId)
                .map(UserResponse::from)
                // The token is still valid, but the account behind it is gone.
                .orElseThrow(() -> ApiException.unauthorized(ErrorCode.UNAUTHENTICATED, "The account no longer exists"));
    }
}
