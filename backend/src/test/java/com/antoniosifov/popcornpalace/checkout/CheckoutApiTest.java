package com.antoniosifov.popcornpalace.checkout;

import com.antoniosifov.popcornpalace.payment.FakePaymentProvider;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paying for a hold: success, declines, provider failures, and above all repeated requests. */
class CheckoutApiTest extends ApiIntegrationTest
{
    private static final String KEY = "purchase-attempt-0001";

    private long hallId;
    private long showtimeId;
    private long seatA;
    private long seatB;

    @BeforeEach
    void createShowtime() throws Exception
    {
        long movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        seatA = seatId(hallId, 1, 1);
        seatB = seatId(hallId, 1, 2);
    }

    // ------------------------------------------------------------ paying

    @DisplayName("Paying for a hold charges the total once and returns the booking with its payment")
    @Test
    void paysAndBooks() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA, seatB);

        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalPrice").value(29.0))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.amount").value(29.0))
                .andExpect(jsonPath("$.payment.currency").value("USD"));

        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(payments()).containsExactly(Map.of("status", "SUCCEEDED", "amount", "29.00"));
    }

    @DisplayName("The Idempotency-Key header is required and must look like a key")
    @Test
    void requiresIdempotencyKey() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        mvc.perform(post(holdsUrl(showtimeId) + "/" + holdId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\": \"tok_visa\"}"))
                .andExpect(problem(400, "IDEMPOTENCY_KEY_REQUIRED"));

        confirm(customerToken, showtimeId, holdId, "short", FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(400, "IDEMPOTENCY_KEY_REQUIRED"));
        confirm(customerToken, showtimeId, holdId, "has spaces and <tags>", FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(400, "IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(paymentProvider.chargeCount()).isZero();
        assertThat(payments()).isEmpty();
    }

    @DisplayName("A payment token is required")
    @Test
    void requiresPaymentToken() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        mvc.perform(post(holdsUrl(showtimeId) + "/" + holdId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("paymentToken"));

        assertThat(payments()).isEmpty();
    }

    // ------------------------------------------------------------ idempotency

    @DisplayName("Repeating a paid request with the same key returns the same booking and does not charge again")
    @Test
    void replaysASuccessfulPayment() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);
        String first = confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // The client never saw the response (say, the connection dropped) and sends the request again.
        MvcResult second = confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(content().json(first, JsonCompareMode.STRICT))
                .andReturn();

        assertThat(second.getResponse().getHeader(HttpHeaders.LOCATION))
                .endsWith("/api/v1/bookings/" + JsonPath.read(first, "$.id"));
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("payments")).isEqualTo(1);
    }

    @DisplayName("A key that was used for one purchase cannot be used for another")
    @Test
    void rejectsKeyReusedForAnotherPurchase() throws Exception
    {
        confirmHoldWithKey(createHold(customerToken, showtimeId, seatA));
        String otherHoldId = createHold(customerToken, showtimeId, seatB);

        confirm(customerToken, showtimeId, otherHoldId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(422, "IDEMPOTENCY_KEY_REUSED"));

        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(count("bookings")).isEqualTo(1);
    }

    @DisplayName("Idempotency keys belong to a user: two customers may use the same key")
    @Test
    void sameKeyForDifferentUsersIsIndependent() throws Exception
    {
        confirmHoldWithKey(createHold(customerToken, showtimeId, seatA));
        String othersHold = createHold(otherCustomerToken, showtimeId, seatB);

        confirm(otherCustomerToken, showtimeId, othersHold, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.userId").value(OTHER_CUSTOMER_ID.toString()));

        assertThat(paymentProvider.chargeCount()).isEqualTo(2);
    }

    // ------------------------------------------------------------ declines and provider failures

    @DisplayName("A declined card is a 402 with the reason; the seats stay held and another card can be tried")
    @Test
    void declinedPaymentKeepsTheHold() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_DECLINED)
                .andExpect(problem(402, "PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.declineCode").value("insufficient_funds"));

        assertThat(paymentProvider.chargeCount()).isZero();
        assertThat(count("bookings")).isZero();
        mvc.perform(get(holdsUrl(showtimeId) + "/mine").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk());

        // A new attempt needs a new key.
        confirm(customerToken, showtimeId, holdId, "purchase-attempt-0002", FakePaymentProvider.TOKEN_OK)
                .andExpect(status().isCreated());
        assertThat(payments()).containsExactly(
                Map.of("status", "FAILED", "amount", "14.50"),
                Map.of("status", "SUCCEEDED", "amount", "14.50"));
    }

    @DisplayName("Repeating a declined request with the same key gets the same decline, even with a working card")
    @Test
    void replaysADecline() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);
        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_EXPIRED)
                .andExpect(problem(402, "PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.declineCode").value("expired_card"));

        // Same key means "the same request"; its recorded answer is returned.
        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(402, "PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.declineCode").value("expired_card"));

        assertThat(paymentProvider.chargeCount()).isZero();
        assertThat(count("payments")).isEqualTo(1);
    }

    @DisplayName("When the provider fails nobody is charged, the seats stay held, and a new attempt can succeed")
    @Test
    void providerFailureChargesNothing() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_ERROR)
                .andExpect(problem(502, "PAYMENT_PROVIDER_UNAVAILABLE"));

        assertThat(paymentProvider.chargeCount()).isZero();
        assertThat(payments()).containsExactly(Map.of("status", "FAILED", "amount", "14.50"));

        confirm(customerToken, showtimeId, holdId, "purchase-attempt-0002", FakePaymentProvider.TOKEN_OK)
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ nothing is charged without a valid hold

    @DisplayName("An expired hold or someone else's hold is never charged")
    @Test
    void doesNotChargeWithoutAValidHold() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        confirm(otherCustomerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(404, "HOLD_NOT_FOUND"));

        expireHolds();
        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(404, "HOLD_NOT_FOUND"));
        confirm(customerToken, showtimeId, UUID.randomUUID().toString(), "purchase-attempt-0002", FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(404, "HOLD_NOT_FOUND"));

        assertThat(paymentProvider.chargeCount()).isZero();
        assertThat(payments()).isEmpty();
    }

    // ------------------------------------------------------------ compensation

    @DisplayName("If the customer is charged but the booking cannot be saved, the charge is refunded")
    @Test
    void refundsWhenTheBookingFailsAfterTheCharge() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);
        // The situation holds normally prevent, forced here: the seat gets sold behind
        // the hold's back (as could happen if Redis lost its data).
        sellDirectly(seatA);

        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(409, "SEAT_ALREADY_BOOKED"));

        // The customer was charged, and got the money back.
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT status, failure_code FROM payments"))
                .containsEntry("status", "REFUNDED")
                .containsEntry("failure_code", "BOOKING_FAILED");
        // Only the booking written directly exists.
        assertThat(count("bookings")).isEqualTo(1);

        // Repeating the request does not charge again; it explains what happened.
        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(409, "PAYMENT_REFUNDED"));
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------ helpers

    private void confirmHoldWithKey(String holdId) throws Exception
    {
        confirm(customerToken, showtimeId, holdId, KEY, FakePaymentProvider.TOKEN_OK).andExpect(status().isCreated());
    }

    private void sellDirectly(long seatId)
    {
        UUID bookingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO bookings (id, showtime_id, hall_id, user_id, total_price, created_at)
                VALUES (?, ?, ?, ?, 14.50, now())""", bookingId, showtimeId, hallId, OTHER_CUSTOMER_ID);
        jdbc.update("""
                INSERT INTO booking_seats (booking_id, showtime_id, hall_id, seat_id, price)
                VALUES (?, ?, ?, ?, 14.50)""", bookingId, showtimeId, hallId, seatId);
    }

    private java.util.List<Map<String, Object>> payments()
    {
        return jdbc.queryForList("SELECT status, amount::text AS amount FROM payments ORDER BY created_at, status");
    }

    private long count(String table)
    {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
