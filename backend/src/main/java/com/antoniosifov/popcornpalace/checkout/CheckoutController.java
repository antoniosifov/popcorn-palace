package com.antoniosifov.popcornpalace.checkout;

import com.antoniosifov.popcornpalace.booking.BookingResponse;
import com.antoniosifov.popcornpalace.common.config.OpenApiConfig;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/holds/{holdId}/confirm")
@Tag(name = "Checkout")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CheckoutController
{
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** Set to "true" on a response that repeats the outcome of an earlier request with the same key. */
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    /** Long enough to be unique (a UUID fits), short enough to store, and safe to log. */
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_.:-]{8,100}");

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService)
    {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @Operation(summary = "Pay for a hold and turn it into a booking", description = """
            Charges the held seats at the prices shown when they were held and creates the booking.

            Requires an `Idempotency-Key` header: any unique value (for example a UUID) generated once per \
            purchase attempt. If the response is lost and the request is sent again with the same key, the \
            customer is not charged again: the first outcome is returned, with `Idempotent-Replayed: true`.

            Fails with 402 when the payment is declined (the seats stay held, retry with a new key), \
            404 when the hold has expired, 409 while another request is paying for the same hold.""")
    public ResponseEntity<BookingResponse> confirm(
            @PathVariable Long showtimeId,
            @PathVariable UUID holdId,
            @Parameter(description = "Unique per purchase attempt, 8-100 characters: letters, digits and _ . : -",
                    example = "3f1c2b9e-7d4a-4c1e-9b0a-2f6d8e5c1a77")
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal Jwt jwt)
    {
        if (idempotencyKey == null || !VALID_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "The Idempotency-Key header is required: 8-100 characters (letters, digits, '_', '.', ':', '-'), "
                            + "unique for each purchase attempt");
        }

        CheckoutService.Result result = checkoutService.confirm(showtimeId, holdId, idempotencyKey, request, CurrentUser.from(jwt));

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/bookings/{id}").build(result.booking().id());
        ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(result.booking());
    }
}
