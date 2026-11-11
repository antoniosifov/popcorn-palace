package com.antoniosifov.popcornpalace.security;

import com.antoniosifov.popcornpalace.user.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Who may call what:
 *
 * <ul>
 *   <li>anyone: register, log in, ask for and use a password reset link, browse movies, halls, showtimes and seat maps, read the API docs;</li>
 *   <li>administrators only: create, change or delete movies, halls and showtimes;</li>
 *   <li>any logged-in account: everything else (holding seats, bookings, own profile).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig
{
    private static final String[] CATALOG = {"/api/v1/movies/**", "/api/v1/halls/**", "/api/v1/showtimes/**"};
    /** Nested under showtimes, but for customers, not for managing the catalog. */
    private static final String[] CHECKOUT = {"/api/v1/showtimes/*/holds", "/api/v1/showtimes/*/holds/**"};
    private static final String[] API_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};
    /** Health and metrics. Served on the management port, which is not published outside the stack. */
    private static final String[] MANAGEMENT = {"/actuator/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemSecurityHandlers problemHandlers) throws Exception
    {
        http
                // No cookies or sessions are used for authentication, so there is
                // nothing for a CSRF attack to ride on.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Uses the CORS rules from WebConfig and answers preflight requests before authentication.
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // Whoever forgot their password cannot be asked to log in first.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/confirm").permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        .requestMatchers(MANAGEMENT).permitAll()
                        // Must come before the catalog rules below: the first matching rule wins.
                        .requestMatchers(CHECKOUT).authenticated()
                        .requestMatchers(HttpMethod.GET, CATALOG).permitAll()
                        .requestMatchers(CATALOG).hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers));
        return http.build();
    }

    /** Turns the token's role claim into the authority that hasRole(...) checks. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter()
    {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(CurrentUser.ROLE_CLAIM))));
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}
