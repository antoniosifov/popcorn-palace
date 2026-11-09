package com.antoniosifov.popcornpalace.checkout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of the confirm request.
 *
 * @param paymentToken stands in for the customer's card. Card numbers are never
 *                     sent to this API; with a real provider the browser would
 *                     exchange them for a token directly with the provider.
 */
public record CheckoutRequest(
        @Schema(example = "tok_visa", description = """
                Sandbox tokens: tok_visa (charged), tok_slow (charged after a delay), \
                tok_declined (insufficient funds), tok_expired (expired card), tok_error (provider failure).""")
        @NotBlank @Size(max = 100)
        String paymentToken
)
{
}
