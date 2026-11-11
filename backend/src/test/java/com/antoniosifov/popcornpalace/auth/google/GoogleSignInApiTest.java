package com.antoniosifov.popcornpalace.auth.google;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.FakeGoogle;
import com.antoniosifov.popcornpalace.support.FakeGoogle.IdToken;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signing in with Google. The tokens are real ones in every respect but who
 * signed them: {@link FakeGoogle} publishes its keys over HTTP and the
 * application fetches and trusts them as it would Google's.
 */
class GoogleSignInApiTest extends ApiIntegrationTest
{
    private static final String GOOGLE = AUTH + "/google";
    private static final String ME = "/api/v1/users/me";
    private static final String EMAIL = "anna@example.com";
    private static final String SUBJECT = "110169484474386276334";
    private static final String PASSWORD = "correct-horse-battery";

    // ------------------------------------------------------------ accounts

    @DisplayName("The first sign-in opens a customer account with the name Google gives, and returns a working token")
    @Test
    void firstSignInOpensAnAccount() throws Exception
    {
        String body = signIn(FakeGoogle.idToken(SUBJECT, " Anna@Example.com ").name("  Anna   Kowalska "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.displayName").value("Anna Kowalska"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();

        whoAmI(JsonPath.read(body, "$.accessToken")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL));
        assertThat(jdbc.queryForMap("SELECT google_subject, email_verified, password_hash FROM users WHERE email = ?", EMAIL))
                .containsEntry("google_subject", SUBJECT)
                .containsEntry("email_verified", true)
                // No password of its own: nothing matches this.
                .containsEntry("password_hash", "!");
    }

    @DisplayName("Signing in again finds the same account, by Google's subject, even after the email changed at Google")
    @Test
    void laterSignInsFindTheAccountBySubject() throws Exception
    {
        String first = userId(signIn(FakeGoogle.idToken(SUBJECT, EMAIL)));

        String second = userId(signIn(FakeGoogle.idToken(SUBJECT, EMAIL)));
        String afterRename = signIn(FakeGoogle.idToken(SUBJECT, "anna.new@example.com"))
                .andExpect(status().isOk())
                // The account keeps the email it was opened with.
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(JsonPath.<String>read(afterRename, "$.user.id")).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE google_subject = ?", Long.class, SUBJECT)).isEqualTo(1);
    }

    @DisplayName("Without a name from Google the account is named after the email")
    @Test
    void namesTheAccountAfterTheEmailWhenGoogleGivesNoName() throws Exception
    {
        signIn(FakeGoogle.idToken(SUBJECT, EMAIL).name(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("anna"));
    }

    @DisplayName("An account that has no password cannot be logged in to with one, until one is set through a reset link")
    @Test
    void anAccountWithoutAPasswordHasNoPassword() throws Exception
    {
        signIn(FakeGoogle.idToken(SUBJECT, EMAIL)).andExpect(status().isOk());

        for (String guess : List.of("!", "", "correct-horse-battery")) {
            login(EMAIL, guess).andExpect(status().is4xxClientError());
        }
        login(EMAIL, "!").andExpect(problem(401, "INVALID_CREDENTIALS"));
    }

    // ------------------------------------------------------------ an email that is already here

    @DisplayName("An account that proved its email is connected and keeps its password")
    @Test
    void connectsAnAccountThatProvedItsEmail() throws Exception
    {
        String registered = userId(register(EMAIL, PASSWORD));
        // Opening a reset link is proof that the email is the registrant's.
        jdbc.update("UPDATE users SET email_verified = TRUE WHERE email = ?", EMAIL);

        String signedIn = userId(signIn(FakeGoogle.idToken(SUBJECT, EMAIL)));

        assertThat(signedIn).isEqualTo(registered);
        login(EMAIL, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT google_subject FROM users WHERE email = ?", String.class, EMAIL)).isEqualTo(SUBJECT);
    }

    @DisplayName("An account opened with somebody's email by somebody else loses its password and sessions when the owner signs in with Google")
    @Test
    void aStrangersPasswordDoesNotSurviveTheOwnerArriving() throws Exception
    {
        // Mallory registers with Anna's email before Anna ever comes here, and stays logged in.
        ResultActions mallory = register(EMAIL, "mallorys-password");
        String mallorysToken = JsonPath.read(mallory.andReturn().getResponse().getContentAsString(), "$.accessToken");
        whoAmI(mallorysToken).andExpect(status().isOk());

        // Anna signs in with Google, which vouches that the email is hers.
        String annasToken = JsonPath.read(signIn(FakeGoogle.idToken(SUBJECT, EMAIL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        // The account is Anna's now, and only hers.
        whoAmI(annasToken).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL));
        login(EMAIL, "mallorys-password").andExpect(problem(401, "INVALID_CREDENTIALS"));
        whoAmI(mallorysToken).andExpect(problem(401, "UNAUTHENTICATED"));
        assertThat(jdbc.queryForMap("SELECT password_hash, email_verified FROM users WHERE email = ?", EMAIL))
                .containsEntry("password_hash", "!")
                .containsEntry("email_verified", true);
    }

    @DisplayName("Setting a password through a reset link proves the email, so a later Google sign-in keeps that password")
    @Test
    void aResetLinkProvesTheEmail() throws Exception
    {
        register(EMAIL, PASSWORD);
        String newPassword = "set-through-the-link";
        anonymous.perform(post(AUTH + "/password-reset").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s"}""".formatted(EMAIL))).andExpect(status().isAccepted());
        String url = JsonPath.read(jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class), "$.passwordReset.url");
        anonymous.perform(post(AUTH + "/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content("""
                {"token": "%s", "password": "%s"}""".formatted(url.substring(url.indexOf("token=") + 6), newPassword)))
                .andExpect(status().isNoContent());

        signIn(FakeGoogle.idToken(SUBJECT, EMAIL)).andExpect(status().isOk());

        login(EMAIL, newPassword).andExpect(status().isOk());
    }

    @DisplayName("An account already connected to one Google account is not handed to another that now has its email")
    @Test
    void doesNotReconnectAnAccountToAnotherGoogleAccount() throws Exception
    {
        signIn(FakeGoogle.idToken(SUBJECT, EMAIL)).andExpect(status().isOk());

        signIn(FakeGoogle.idToken("another-google-account", EMAIL)).andExpect(problem(409, "EMAIL_TAKEN"));

        assertThat(jdbc.queryForObject("SELECT google_subject FROM users WHERE email = ?", String.class, EMAIL)).isEqualTo(SUBJECT);
    }

    // ------------------------------------------------------------ tokens that must not work

    @DisplayName("An address Google has not confirmed opens nothing")
    @Test
    void refusesAnEmailGoogleHasNotConfirmed() throws Exception
    {
        signIn(FakeGoogle.idToken(SUBJECT, EMAIL).emailNotConfirmed()).andExpect(problem(401, "GOOGLE_EMAIL_UNVERIFIED"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, EMAIL)).isZero();
    }

    @DisplayName("A token that fails any check is refused, with one answer that does not say which check")
    @Test
    void refusesTokensThatFailACheck() throws Exception
    {
        Map<String, IdToken> bad = new LinkedHashMap<>();
        // Really from Google, but issued when the person signed in to some other site.
        bad.put("issued for another application", FakeGoogle.idToken(SUBJECT, EMAIL).issuedFor("another-site.apps.googleusercontent.com"));
        bad.put("issued by somebody else", FakeGoogle.idToken(SUBJECT, EMAIL).issuedBy("https://accounts.example.com"));
        bad.put("expired", FakeGoogle.idToken(SUBJECT, EMAIL).expired());
        bad.put("signed with a key Google never published", FakeGoogle.idToken(SUBJECT, EMAIL).forged());

        for (Map.Entry<String, IdToken> entry : bad.entrySet()) {
            IdToken token = entry.getValue();
            String detail = JsonPath.read(signIn(token.signed(), token.nonce())
                    .andExpect(problem(401, "GOOGLE_SIGN_IN_FAILED"))
                    .andReturn().getResponse().getContentAsString(), "$.detail");
            assertThat(detail).as(entry.getKey()).isEqualTo("Signing in with Google did not work. Try again.");
        }
        signIn("not.a.token", "nonce").andExpect(problem(401, "GOOGLE_SIGN_IN_FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, EMAIL)).isZero();
    }

    @DisplayName("A token that answers another sign-in than the one this browser started is refused")
    @Test
    void refusesATokenWithAnotherNonce() throws Exception
    {
        IdToken token = FakeGoogle.idToken(SUBJECT, EMAIL).nonce("the-nonce-of-the-sign-in-it-was-issued-for");

        signIn(token.signed(), "the-nonce-of-this-browser").andExpect(problem(401, "GOOGLE_SIGN_IN_FAILED"));
        signIn(token.signed(), token.nonce()).andExpect(status().isOk());
    }

    @DisplayName("A request without a token or a nonce is rejected before anything is checked")
    @Test
    void rejectsAnIncompleteRequest() throws Exception
    {
        anonymous.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(problem(400, "VALIDATION_FAILED"));
    }

    @DisplayName("Without a client id signing in with Google does not exist")
    @Test
    void isOffWithoutAClientId()
    {
        GoogleIdTokenVerifier unconfigured = new GoogleIdTokenVerifier(
                new GoogleProperties("", FakeGoogle.jwkSetUri(), List.of(FakeGoogle.ISSUER)));
        IdToken token = FakeGoogle.idToken(SUBJECT, EMAIL);

        assertThatThrownBy(() -> unconfigured.verify(token.signed(), token.nonce()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.GOOGLE_SIGN_IN_DISABLED);
                    assertThat(exception.getStatus().value()).isEqualTo(404);
                });
    }

    // ------------------------------------------------------------ helpers

    private ResultActions signIn(IdToken token) throws Exception
    {
        return signIn(token.signed(), token.nonce());
    }

    private ResultActions signIn(String idToken, String nonce) throws Exception
    {
        return anonymous.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content("""
                {"idToken": "%s", "nonce": "%s"}
                """.formatted(idToken, nonce)));
    }

    private ResultActions register(String email, String password) throws Exception
    {
        return anonymous.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "displayName": "Anna"}
                        """.formatted(email, password)))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String email, String password) throws Exception
    {
        return anonymous.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    private ResultActions whoAmI(String accessToken) throws Exception
    {
        return anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
    }

    private static String userId(ResultActions answer) throws Exception
    {
        return JsonPath.read(answer.andReturn().getResponse().getContentAsString(), "$.user.id");
    }
}
