package com.antoniosifov.popcornpalace.common;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Errors raised by the framework itself use the same response format as business errors. */
class ErrorResponseApiTest extends ApiIntegrationTest
{
    @DisplayName("An unknown path returns a 404 problem response")
    @Test
    void unknownPath() throws Exception
    {
        mvc.perform(get("/api/v1/popcorn"))
                .andExpect(problem(404, "NOT_FOUND"));
    }

    @DisplayName("An unsupported HTTP method returns a 405 problem response")
    @Test
    void unsupportedMethod() throws Exception
    {
        mvc.perform(patch(MOVIES))
                .andExpect(problem(405, "METHOD_NOT_ALLOWED"));
    }

    @DisplayName("A body that is not JSON returns a 415 problem response")
    @Test
    void unsupportedMediaType() throws Exception
    {
        mvc.perform(post(MOVIES).contentType(MediaType.TEXT_PLAIN).content("Interstellar"))
                .andExpect(problem(415, "UNSUPPORTED_MEDIA_TYPE"));
    }

    @DisplayName("The OpenAPI description is served and lists every resource")
    @Test
    void openApiDescription() throws Exception
    {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Popcorn Palace API"))
                .andExpect(jsonPath("$.paths['/api/v1/movies']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/movies/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/showtimes']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/showtimes/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings/{id}']").exists());
    }
}
