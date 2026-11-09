package com.antoniosifov.popcornpalace.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Metadata shown at the top of Swagger UI ({@code /swagger-ui.html}). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Popcorn Palace API",
        version = "v1",
        description = """
                Movie ticket booking API: manage the movie catalog, schedule showtimes and book seats.
                Errors are returned as RFC 9457 problem details with a machine-readable `code` field.

                Browsing is public. To call the other endpoints, register or log in, then press \
                **Authorize** and paste the `accessToken`.
                """,
        contact = @Contact(name = "Anton Iosifov", url = "https://antoniosifov.com")
))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig
{
    /** Name of the security scheme that protected operations refer to. */
    public static final String BEARER_AUTH = "bearerAuth";
}
