package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.common.config.OpenApiConfig;
import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Seat reservations for a showtime")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BookingController
{
    private final BookingService bookingService;

    public BookingController(BookingService bookingService)
    {
        this.bookingService = bookingService;
    }

    @GetMapping
    @Operation(summary = "List my bookings", description = "Paginated, newest first. Sortable by createdAt, price.")
    public PageResponse<BookingResponse> listOwn(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt)
    {
        return bookingService.listOwn(CurrentUser.from(jwt), pageable);
    }

    @PostMapping
    @Operation(summary = "Book a seat", description = """
            Books the seat for the logged-in user. Fails with 409 when the seat is \
            already booked or the showtime has started.""")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request, @AuthenticationPrincipal Jwt jwt)
    {
        BookingResponse created = bookingService.create(request, CurrentUser.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking", description = "Only the owner and administrators can see a booking; for anyone else it returns 404.")
    public BookingResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt)
    {
        return bookingService.get(id, CurrentUser.from(jwt));
    }
}
