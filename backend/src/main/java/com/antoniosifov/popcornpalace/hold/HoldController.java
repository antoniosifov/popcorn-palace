package com.antoniosifov.popcornpalace.hold;

import com.antoniosifov.popcornpalace.booking.BookingResponse;
import com.antoniosifov.popcornpalace.common.config.OpenApiConfig;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/holds")
@Tag(name = "Checkout", description = "Hold seats while checking out, then confirm the hold into a booking")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class HoldController
{
    private final HoldService holdService;

    public HoldController(HoldService holdService)
    {
        this.holdService = holdService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Hold seats", description = """
            Reserves all the given seats for the logged-in user for a limited time (10 minutes by default), \
            or none of them. Fails with 409 when a seat is held by someone else or already booked, \
            and when the user already has a hold for this showtime.""")
    public HoldResponse create(@PathVariable Long showtimeId, @Valid @RequestBody HoldRequest request,
                               @AuthenticationPrincipal Jwt jwt)
    {
        return holdService.create(showtimeId, request, CurrentUser.from(jwt));
    }

    @GetMapping("/mine")
    @Operation(summary = "My current hold for this showtime", description = "Returns 404 when there is none or it has expired.")
    public HoldResponse getOwn(@PathVariable Long showtimeId, @AuthenticationPrincipal Jwt jwt)
    {
        return holdService.getOwn(showtimeId, CurrentUser.from(jwt));
    }

    @DeleteMapping("/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Release a hold", description = "Gives the seats back right away. Succeeds even if the hold has already expired.")
    public void release(@PathVariable Long showtimeId, @PathVariable UUID holdId, @AuthenticationPrincipal Jwt jwt)
    {
        holdService.release(showtimeId, holdId, CurrentUser.from(jwt));
    }

    @PostMapping("/{holdId}/confirm")
    @Operation(summary = "Confirm a hold into a booking", description = """
            Creates the booking for the held seats at the prices shown when they were held. \
            Fails with 404 when the hold has expired.""")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long showtimeId, @PathVariable UUID holdId,
                                                   @AuthenticationPrincipal Jwt jwt)
    {
        BookingResponse booking = holdService.confirm(showtimeId, holdId, CurrentUser.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/bookings/{id}").build(booking.id());
        return ResponseEntity.created(location).body(booking);
    }
}
