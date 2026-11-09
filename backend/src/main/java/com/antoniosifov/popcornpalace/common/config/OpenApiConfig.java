package com.antoniosifov.popcornpalace.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Metadata shown at the top of Swagger UI ({@code /swagger-ui.html}). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Popcorn Palace API",
        version = "v1",
        description = """
                Movie ticket booking API: manage the movie catalog, schedule showtimes and book seats.
                Errors are returned as RFC 9457 problem details with a machine-readable `code` field.
                """,
        contact = @Contact(name = "Anton Iosifov", url = "https://antoniosifov.com")
))
public class OpenApiConfig
{
}
