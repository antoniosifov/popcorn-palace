package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.security.SecurityProperties;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBootstrapTest extends ApiIntegrationTest
{
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    @DisplayName("The configured administrator is created and can log in and manage the catalog")
    @Test
    void createsAdministratorThatCanLogIn() throws Exception
    {
        bootstrap("Boss@Example.com", "boss-password-1").run(null);

        String body = login("boss@example.com", "boss-password-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        postJsonAs(token, MOVIES, movieJson("Interstellar")).andExpect(status().isCreated());
    }

    @DisplayName("Starting again never resets the password of an existing administrator")
    @Test
    void doesNotResetAnExistingAccount() throws Exception
    {
        bootstrap("boss@example.com", "boss-password-1").run(null);
        bootstrap("boss@example.com", "a-different-password").run(null);

        login("boss@example.com", "boss-password-1").andExpect(status().isOk());
        login("boss@example.com", "a-different-password").andExpect(problem(401, "INVALID_CREDENTIALS"));
    }

    @DisplayName("Nothing is created when no administrator is configured")
    @Test
    void doesNothingWithoutConfiguration()
    {
        long before = userRepository.count();

        bootstrap("", "").run(null);
        bootstrap("boss@example.com", null).run(null);

        assertThat(userRepository.count()).isEqualTo(before);
    }

    @DisplayName("A too-short administrator password stops the startup instead of creating a weak account")
    @Test
    void rejectsShortPassword()
    {
        assertThatThrownBy(() -> bootstrap("boss@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    private AdminBootstrap bootstrap(String email, String password)
    {
        SecurityProperties properties = new SecurityProperties(null, new SecurityProperties.Admin(email, password));
        return new AdminBootstrap(userRepository, passwordEncoder, clock, properties);
    }

    private ResultActions login(String email, String password) throws Exception
    {
        return anonymous.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }
}
