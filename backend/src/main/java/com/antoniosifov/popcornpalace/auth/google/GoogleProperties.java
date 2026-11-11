package com.antoniosifov.popcornpalace.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Settings under {@code app.google}.
 *
 * @param clientId  the OAuth client the web client signs people in with; an ID token is
 *                  accepted only if it was issued for it. Empty: signing in with Google is off
 * @param jwkSetUri where Google publishes the keys it signs ID tokens with
 * @param issuers   the values Google puts into the {@code iss} claim
 */
@ConfigurationProperties("app.google")
public record GoogleProperties(String clientId, String jwkSetUri, List<String> issuers)
{
    public boolean enabled()
    {
        return clientId != null && !clientId.isBlank();
    }
}
