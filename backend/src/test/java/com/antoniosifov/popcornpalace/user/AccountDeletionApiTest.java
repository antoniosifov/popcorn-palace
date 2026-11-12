package com.antoniosifov.popcornpalace.user;

import com.antoniosifov.popcornpalace.auth.event.AccountEvent;
import com.antoniosifov.popcornpalace.booking.event.BookingEvent;
import com.antoniosifov.popcornpalace.outbox.OutboxRelay;
import com.antoniosifov.popcornpalace.payment.PaymentReconciler;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.FakeGoogle;
import com.antoniosifov.popcornpalace.support.KafkaTestConsumer;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An account deleted by its owner: what goes, what stays, and what happens to the tickets. */
class AccountDeletionApiTest extends ApiIntegrationTest
{
    private static final String ME = "/api/v1/users/me";
    private static final String DELETE = ME + "/delete";
    private static final String ACCOUNT_TOPIC = "popcorn.account-events";

    private static final String EMAIL = "anna@example.com";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String GOOGLE_SUBJECT = "108204268033311374519";

    @Autowired
    private OutboxRelay relay;
    @Autowired
    private KafkaContainer kafka;
    @Autowired
    private PaymentReconciler reconciler;

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

    // ------------------------------------------------------------ what goes

    @DisplayName("Deleting an account erases everything that says whose it was, and keeps the row")
    @Test
    void erasesTheAccount() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);

        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM users WHERE id = ?::uuid", anna.id());
        assertThat(row.get("email")).isEqualTo("deleted-" + anna.id() + "@deleted.invalid");
        assertThat(row.get("display_name")).isEqualTo("Deleted customer");
        // Not a hash of anything: no password matches it.
        assertThat(row.get("password_hash")).isEqualTo("!");
        assertThat(row.get("google_subject")).isNull();
        assertThat(row.get("email_verified")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(count("users WHERE email = '" + EMAIL + "'")).isZero();
    }

    @DisplayName("Nobody can log in to a deleted account, and whoever was logged in is not any more")
    @Test
    void endsSessionsAndLogins() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        String secondSession = JsonPath.read(login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString(), "$.accessToken");

        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());

        whoAmI(anna.token()).andExpect(status().isUnauthorized());
        whoAmI(secondSession).andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD).andExpect(problem(401, "INVALID_CREDENTIALS"));
        // Other people are not touched.
        whoAmI(customerToken).andExpect(status().isOk());
    }

    @DisplayName("If Redis forgets that the sessions were ended, a token of a deleted account still opens nothing")
    @Test
    void aDeletedAccountIsGoneEvenForAForgetfulRedis() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());
        forgetEverythingInRedis();

        whoAmI(anna.token()).andExpect(problem(401, "UNAUTHENTICATED"));
        delete(anna.token(), PASSWORD).andExpect(problem(401, "UNAUTHENTICATED"));
    }

    @DisplayName("The email is free at once: registering with it again opens a new account with none of the old bookings")
    @Test
    void theEmailCanBeUsedAgain() throws Exception
    {
        Account before = register(EMAIL, PASSWORD);
        createBookingAs(before.token(), showtimeId, seatA);
        delete(before.token(), PASSWORD).andExpect(status().isNoContent());

        Account after = register(EMAIL, "another-horse-battery");

        assertThat(after.id()).isNotEqualTo(before.id());
        mvc.perform(get(BOOKINGS).header(HttpHeaders.AUTHORIZATION, bearer(after.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @DisplayName("A link to set a new password that was on its way stops working")
    @Test
    void aResetLinkDiesWithTheAccount() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        anonymous.perform(post(AUTH + "/password-reset").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s"}
                """.formatted(EMAIL))).andExpect(status().isAccepted());
        String url = JsonPath.read(newestEvent(), "$.passwordReset.url");
        String resetToken = url.substring(url.indexOf("token=") + "token=".length());

        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());

        assertThat(count("password_reset_tokens")).isZero();
        anonymous.perform(post(AUTH + "/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content("""
                {"token": "%s", "password": "a-new-horse-battery"}
                """.formatted(resetToken))).andExpect(problem(400, "RESET_LINK_INVALID"));
    }

    // ------------------------------------------------------------ who may

    @DisplayName("An account with a password is deleted only by somebody who knows it")
    @Test
    void asksForThePassword() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);

        delete(anna.token(), "not-the-password").andExpect(problem(400, "PASSWORD_INCORRECT"));
        delete(anna.token(), null).andExpect(problem(400, "PASSWORD_INCORRECT"));
        mvc.perform(post(DELETE).header(HttpHeaders.AUTHORIZATION, bearer(anna.token())))
                .andExpect(problem(400, "PASSWORD_INCORRECT"));

        // Nothing happened to it.
        whoAmI(anna.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.hasPassword").value(true));
        assertThat(count("outbox_events")).isZero();
    }

    @DisplayName("An account opened with Google has no password to ask for, and Google no longer leads to it afterwards")
    @Test
    void aGoogleAccountNeedsNoPassword() throws Exception
    {
        Account anna = signInWithGoogle();
        whoAmI(anna.token()).andExpect(jsonPath("$.hasPassword").value(false));

        mvc.perform(post(DELETE).header(HttpHeaders.AUTHORIZATION, bearer(anna.token())))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT google_subject FROM users WHERE id = ?::uuid", String.class, anna.id())).isNull();
        // Signing in with the same Google account starts from nothing.
        Account again = signInWithGoogle();
        assertThat(again.id()).isNotEqualTo(anna.id());
        whoAmI(again.token()).andExpect(jsonPath("$.email").value(EMAIL));
    }

    @DisplayName("An administrator account cannot be deleted")
    @Test
    void refusesAnAdministrator() throws Exception
    {
        mvc.perform(post(DELETE).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(problem(409, "ACCOUNT_NOT_DELETABLE"));

        whoAmI(adminToken).andExpect(status().isOk());
    }

    @DisplayName("Deleting an account needs an access token")
    @Test
    void needsAuthentication() throws Exception
    {
        anonymous.perform(post(DELETE)).andExpect(problem(401, "UNAUTHENTICATED"));
    }

    @DisplayName("Guessing the password here counts against the same limit as guessing it at login")
    @Test
    void isThrottledLikeLogin() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        // One request was the registration; the limit is ten a minute.
        for (int attempt = 1; attempt <= 9; attempt++) {
            delete(anna.token(), "guess-" + attempt).andExpect(problem(400, "PASSWORD_INCORRECT"));
        }

        delete(anna.token(), PASSWORD).andExpect(problem(429, "TOO_MANY_REQUESTS"));
        login(EMAIL, PASSWORD).andExpect(problem(429, "TOO_MANY_REQUESTS"));
    }

    // ------------------------------------------------------------ the tickets

    @DisplayName("Tickets that could still be used are cancelled and refunded; what has been watched stays on record")
    @Test
    void cancelsUpcomingBookings() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        long watchedShowtimeId = createShowtime(createMovie("Arrival"), hallId, "2030-01-16T18:00:00Z", "2030-01-16T20:00:00Z");
        String watched = createBookingAs(anna.token(), watchedShowtimeId, seatA);
        jdbc.update("UPDATE showtimes SET start_time = now() - interval '3 hours', end_time = now() - interval '1 hour' WHERE id = ?",
                watchedShowtimeId);
        String upcoming = createBookingAs(anna.token(), showtimeId, seatA, seatB);
        String someoneElses = createBooking(showtimeId, seatId(hallId, 2, 1));

        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());

        assertThat(bookingStatus(upcoming)).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(upcoming)).isEqualTo("REFUNDED");
        assertThat(bookingStatus(watched)).isEqualTo("CONFIRMED");
        assertThat(paymentStatusOf(watched)).isEqualTo("SUCCEEDED");
        assertThat(bookingStatus(someoneElses)).isEqualTo("CONFIRMED");
        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        // The seats are on sale again, except the one somebody else has.
        anonymous.perform(get(SHOWTIMES + "/" + showtimeId + "/seats"))
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT - 1))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"));
        // Both bookings still point at the row that was the account: the sale is on record, its buyer is not.
        assertThat(count("bookings WHERE user_id = '" + anna.id() + "'")).isEqualTo(2);
    }

    @DisplayName("If the payment provider is down, the account is deleted all the same and the refund is made later")
    @Test
    void theRefundIsRetriedWhenTheProviderIsDown() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        String booking = createBookingAs(anna.token(), showtimeId, seatA);
        paymentProvider.setRefundsFailing(true);

        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());

        assertThat(bookingStatus(booking)).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(booking)).isEqualTo("REFUND_PENDING");
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM users WHERE id = ?::uuid", Instant.class, anna.id())).isNotNull();

        paymentProvider.setRefundsFailing(false);
        Instant inTheFuture = Instant.now().plusSeconds(3600);
        reconciler.reconcile(inTheFuture, inTheFuture);

        assertThat(paymentStatusOf(booking)).isEqualTo("REFUNDED");
        assertThat(paymentProvider.refundCount()).isEqualTo(1);
    }

    @DisplayName("Two requests to delete the same account at once delete it once")
    @Test
    void deletesOnceUnderARace() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        createBookingAs(anna.token(), showtimeId, seatA);

        List<Integer> answers = SimultaneousRequests.run(2,
                () -> delete(anna.token(), PASSWORD).andReturn().getResponse().getStatus());

        assertThat(answers).containsExactlyInAnyOrder(204, 401);
        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        assertThat(count("outbox_events WHERE event_type = '" + AccountEvent.ACCOUNT_DELETED + "'")).isEqualTo(1);
    }

    @DisplayName("A purchase that arrives for an account already deleted is refused, and the charge is refunded")
    @Test
    void nothingCanBeBookedForADeletedAccount() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());
        // The worst case: the token is still taken at its signature.
        forgetEverythingInRedis();
        String holdId = createHold(anna.token(), showtimeId, seatA);

        confirm(anna.token(), showtimeId, holdId).andExpect(problem(401, "UNAUTHENTICATED"));

        assertThat(count("bookings")).isZero();
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(paymentProvider.refundCount()).isEqualTo(1);
    }

    @DisplayName("A purchase and a deletion at the same moment: whichever wins, no ticket is left without an owner and no money is kept")
    @Test
    void aPurchaseRacingADeletionLeavesNothingBehind() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        String holdId = createHold(anna.token(), showtimeId, seatA);
        AtomicInteger turn = new AtomicInteger();

        List<Integer> answers = SimultaneousRequests.run(2, () -> turn.getAndIncrement() == 0
                ? confirm(anna.token(), showtimeId, holdId).andReturn().getResponse().getStatus()
                : delete(anna.token(), PASSWORD).andReturn().getResponse().getStatus());

        // The deletion goes through either way. The purchase was either saved first and then
        // cancelled with the account, or came second and was refused; both give the money back.
        assertThat(answers).contains(204);
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM users WHERE id = ?::uuid", Instant.class, anna.id())).isNotNull();
        assertThat(count("bookings WHERE status = 'CONFIRMED'")).isZero();
        assertThat(count("booking_seats WHERE active")).isZero();
        assertThat(paymentProvider.refundCount()).isEqualTo(paymentProvider.chargeCount());
        assertThat(count("payments WHERE status = 'SUCCEEDED'")).isZero();
    }

    // ------------------------------------------------------------ the email, elsewhere

    @DisplayName("The customer is told once more, by an event that says how many bookings were cancelled")
    @Test
    void announcesTheDeletion() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        createBookingAs(anna.token(), showtimeId, seatA);

        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());

        String event = newestEvent();
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("AccountDeleted");
        assertThat(JsonPath.<String>read(event, "$.account.id")).isEqualTo(anna.id());
        // The address it had, not the one it has now: this is where the last email goes.
        assertThat(JsonPath.<String>read(event, "$.account.email")).isEqualTo(EMAIL);
        assertThat(JsonPath.<String>read(event, "$.account.displayName")).isEqualTo("Anna");
        assertThat(JsonPath.<Integer>read(event, "$.deletion.cancelledBookings")).isEqualTo(1);
    }

    @DisplayName("Events about the customer do not stay in the outbox: sent ones are removed, waiting ones go as soon as they are sent")
    @Test
    void theEmailDoesNotStayInTheOutbox() throws Exception
    {
        Account anna = register(EMAIL, PASSWORD);
        createBookingAs(anna.token(), showtimeId, seatA);
        createBooking(showtimeId, seatB);
        // Both confirmations are sent and, like any event, kept for a while.
        assertThat(relay.publishPending()).isEqualTo(2);
        assertThat(count("outbox_events")).isEqualTo(2);

        delete(anna.token(), PASSWORD).andExpect(status().isNoContent());

        // Anna's confirmation is gone. Her cancellation and the farewell wait to be sent, marked to be deleted after.
        assertThat(count("outbox_events WHERE payload::text LIKE '%" + EMAIL + "%' AND published_at IS NOT NULL")).isZero();
        assertThat(jdbc.queryForList("SELECT event_type FROM outbox_events WHERE confidential ORDER BY id", String.class))
                .containsExactly(BookingEvent.CANCELLED, AccountEvent.ACCOUNT_DELETED);

        assertThat(relay.publishPending()).isEqualTo(2);

        // What is left is the other customer's confirmation.
        assertThat(count("outbox_events WHERE payload::text LIKE '%" + EMAIL + "%'")).isZero();
        assertThat(jdbc.queryForList("SELECT payload -> 'customer' ->> 'email' FROM outbox_events", String.class))
                .containsExactly("customer@test.local");
        // And the farewell did reach Kafka, where the notification service reads it.
        List<ConsumerRecord<String, String>> delivered = KafkaTestConsumer.readByKey(
                kafka.getBootstrapServers(), ACCOUNT_TOPIC, anna.id(), 1, Duration.ofSeconds(20));
        assertThat(delivered).hasSize(1);
        assertThat(JsonPath.<String>read(delivered.getFirst().value(), "$.account.email")).isEqualTo(EMAIL);
    }

    // ------------------------------------------------------------ helpers

    private record Account(String id, String token)
    {
        static Account from(String authResponse)
        {
            return new Account(JsonPath.read(authResponse, "$.user.id"), JsonPath.read(authResponse, "$.accessToken"));
        }
    }

    private Account register(String email, String password) throws Exception
    {
        return Account.from(anonymous.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "displayName": "Anna"}
                        """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private Account signInWithGoogle() throws Exception
    {
        FakeGoogle.IdToken token = FakeGoogle.idToken(GOOGLE_SUBJECT, EMAIL);
        return Account.from(anonymous.perform(post(AUTH + "/google").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idToken": "%s", "nonce": "%s"}
                        """.formatted(token.signed(), token.nonce())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions login(String email, String password) throws Exception
    {
        return anonymous.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    /** @param password null to send a body without one */
    private ResultActions delete(String accessToken, String password) throws Exception
    {
        String body = password == null ? "{}" : "{\"password\": \"%s\"}".formatted(password);
        return anonymous.perform(post(DELETE).header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions whoAmI(String accessToken) throws Exception
    {
        return anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
    }

    private String bookingStatus(String bookingId)
    {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?::uuid", String.class, bookingId);
    }

    private String paymentStatusOf(String bookingId)
    {
        return jdbc.queryForObject(
                "SELECT p.status FROM payments p JOIN bookings b ON b.payment_id = p.id WHERE b.id = ?::uuid", String.class, bookingId);
    }

    private String newestEvent()
    {
        return jdbc.queryForObject("SELECT payload::text FROM outbox_events ORDER BY id DESC LIMIT 1", String.class);
    }

    /** As if Redis had been restarted: it no longer knows which sessions were ended (nor who was throttled). */
    private void forgetEverythingInRedis()
    {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private long count(String tableAndCondition)
    {
        return jdbc.queryForObject("SELECT count(*) FROM " + tableAndCondition, Long.class);
    }
}
