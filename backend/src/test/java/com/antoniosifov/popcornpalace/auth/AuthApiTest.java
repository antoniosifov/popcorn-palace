package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiIntegrationTest
{
    private static final String REGISTER = AUTH + "/register";
    private static final String LOGIN = AUTH + "/login";
    private static final String ME = "/api/v1/users/me";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private JwtEncoder jwtEncoder;

    // ------------------------------------------------------------ registration

    @DisplayName("Registering creates a customer account and returns a working access token")
    @Test
    void registersCustomerAndLogsThemIn() throws Exception
    {
        String body = register("  Anna@Example.com ", PASSWORD, " Anna ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.id").isString())
                .andExpect(jsonPath("$.user.email").value("anna@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Anna"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                // Nothing about the password ever comes back.
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @DisplayName("Only a BCrypt hash of the password is stored")
    @Test
    void storesOnlyAHashOfThePassword() throws Exception
    {
        register("anna@example.com", PASSWORD, "Anna").andExpect(status().isCreated());

        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'anna@example.com'", String.class);
        assertThat(stored).startsWith("$2").doesNotContain(PASSWORD);
    }

    @DisplayName("A registration cannot make itself an administrator")
    @Test
    void registrationCannotChooseARole() throws Exception
    {
        String body = anonymous.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "mallory@example.com", "password": "%s", "displayName": "Mallory", "role": "ADMIN"}
                        """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        postJsonAs(token, MOVIES, movieJson("Interstellar"))
                .andExpect(problem(403, "FORBIDDEN"));
    }

    @DisplayName("An email can be registered once, whatever its letter case")
    @Test
    void rejectsDuplicateEmail() throws Exception
    {
        register("anna@example.com", PASSWORD, "Anna").andExpect(status().isCreated());

        register("ANNA@example.com", PASSWORD, "Another Anna")
                .andExpect(problem(409, "EMAIL_TAKEN"));
    }

    @DisplayName("An invalid registration is rejected with one error per field")
    @Test
    void rejectsInvalidRegistration() throws Exception
    {
        register("not-an-email", "short", "  ")
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password", "displayName")));
    }

    @DisplayName("A password longer than BCrypt can use (72 bytes) is rejected instead of silently cut")
    @Test
    void rejectsPasswordTooLongInBytes() throws Exception
    {
        String fortyAccentedLetters = "é".repeat(40); // 40 characters, 80 bytes

        register("anna@example.com", fortyAccentedLetters, "Anna")
                .andExpect(problem(400, "VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------ login

    @DisplayName("Logging in with the right password returns a working access token")
    @Test
    void logsIn() throws Exception
    {
        register("anna@example.com", PASSWORD, "Anna").andExpect(status().isCreated());

        String body = login("Anna@Example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("anna@example.com"))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anna"));
    }

    @DisplayName("A wrong password and an unknown email get the same answer")
    @Test
    void rejectsBadCredentialsWithoutSayingWhichPartIsWrong() throws Exception
    {
        register("anna@example.com", PASSWORD, "Anna").andExpect(status().isCreated());

        login("anna@example.com", "wrong-password")
                .andExpect(problem(401, "INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));

        login("nobody@example.com", PASSWORD)
                .andExpect(problem(401, "INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
    }

    @DisplayName("An account without a usable password hash cannot be logged into")
    @Test
    void placeholderAccountCannotLogIn() throws Exception
    {
        // The accounts created by the test base class have '!' as their hash,
        // like the placeholder accounts created by migration V3.
        login("customer@test.local", "!")
                .andExpect(problem(401, "INVALID_CREDENTIALS"));
    }

    @DisplayName("Login needs both fields")
    @Test
    void rejectsIncompleteLogin() throws Exception
    {
        anonymous.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password")));
    }

    // ------------------------------------------------------------ tokens

    @DisplayName("A request without a token is asked to authenticate")
    @Test
    void requiresToken() throws Exception
    {
        anonymous.perform(get(ME))
                .andExpect(problem(401, "UNAUTHENTICATED"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"));
    }

    @DisplayName("Text that is not a token is rejected")
    @Test
    void rejectsGarbageToken() throws Exception
    {
        anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(problem(401, "UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired"));
    }

    @DisplayName("An expired token is rejected")
    @Test
    void rejectsExpiredToken() throws Exception
    {
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        String expired = token(jwtEncoder, "popcorn-palace", twoHoursAgo, twoHoursAgo.plus(1, ChronoUnit.HOURS));

        anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(problem(401, "UNAUTHENTICATED"));
    }

    @DisplayName("A token signed with a different key is rejected")
    @Test
    void rejectsTokenSignedWithAnotherKey() throws Exception
    {
        byte[] someoneElsesKey = "a-key-the-application-does-not-know".getBytes(StandardCharsets.UTF_8);
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(someoneElsesKey, "HmacSHA256")));
        String forged = token(forger, "popcorn-palace", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

        anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(problem(401, "UNAUTHENTICATED"));
    }

    @DisplayName("A correctly signed token from another issuer is rejected")
    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception
    {
        String foreign = token(jwtEncoder, "some-other-service", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

        anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(foreign)))
                .andExpect(problem(401, "UNAUTHENTICATED"));
    }

    @DisplayName("Request objects do not reveal the password when printed")
    @Test
    void requestsHideThePasswordInToString()
    {
        assertThat(new RegisterRequest("anna@example.com", PASSWORD, "Anna").toString()).doesNotContain(PASSWORD);
        assertThat(new LoginRequest("anna@example.com", PASSWORD).toString()).doesNotContain(PASSWORD);
    }

    // ------------------------------------------------------------ helpers

    private ResultActions register(String email, String password, String displayName) throws Exception
    {
        return anonymous.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "displayName": "%s"}
                """.formatted(email, password, displayName)));
    }

    private ResultActions login(String email, String password) throws Exception
    {
        return anonymous.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    /** Builds an administrator token for an existing account with full control over issuer and validity. */
    private static String token(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt)
    {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(ADMIN_ID.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", "ADMIN")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
