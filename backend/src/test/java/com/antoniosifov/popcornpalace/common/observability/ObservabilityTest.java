package com.antoniosifov.popcornpalace.common.observability;

import com.antoniosifov.popcornpalace.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health and metrics as an operator sees them: over HTTP, on the management
 * port, with the application running in a real server (not MockMvc), so the
 * port split and the filters are the real ones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class ObservabilityTest
{
    private record Response(int status, String body, String traceId)
    {
    }

    @Value("${local.server.port}")
    private int apiPort;
    @Value("${local.management.port}")
    private int managementPort;

    private final RestClient http = RestClient.create();

    @DisplayName("Health reports every backing service, and the outbox")
    @Test
    void healthReportsEveryDependency()
    {
        Response health = get(management("/actuator/health"));

        assertThat(health.status()).isEqualTo(200);
        assertThat(JsonPath.<String>read(health.body(), "$.status")).isEqualTo("UP");
        assertThat(JsonPath.<String>read(health.body(), "$.components.db.status")).isEqualTo("UP");
        assertThat(JsonPath.<String>read(health.body(), "$.components.redis.status")).isEqualTo("UP");
        assertThat(JsonPath.<String>read(health.body(), "$.components.kafka.status")).isEqualTo("UP");
        assertThat(JsonPath.<Integer>read(health.body(), "$.components.kafka.details.nodes")).isEqualTo(1);
        assertThat(JsonPath.<String>read(health.body(), "$.components.outbox.status")).isEqualTo("UP");
        assertThat(JsonPath.<Integer>read(health.body(), "$.components.outbox.details.pendingEvents")).isZero();
    }

    @DisplayName("Liveness and readiness probes answer separately")
    @Test
    void probesAnswer()
    {
        assertThat(get(management("/actuator/health/liveness")).status()).isEqualTo(200);
        Response readiness = get(management("/actuator/health/readiness"));
        assertThat(readiness.status()).isEqualTo(200);
        assertThat(JsonPath.<String>read(readiness.body(), "$.status")).isEqualTo("UP");
    }

    @DisplayName("Management endpoints are not served on the API port")
    @Test
    void managementIsNotOnTheApiPort()
    {
        assertThat(get(api("/actuator/health")).status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(get(api("/actuator/prometheus")).status()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @DisplayName("Prometheus gets request timings and the business metrics, labelled with the application")
    @Test
    void prometheusExposesRequestAndBusinessMetrics()
    {
        assertThat(get(api("/api/v1/movies")).status()).isEqualTo(200);

        Response metrics = get(management("/actuator/prometheus"));

        assertThat(metrics.status()).isEqualTo(200);
        assertThat(metrics.body())
                .contains("http_server_requests_seconds_count{")
                .contains("uri=\"/api/v1/movies\"")
                .contains("application=\"popcorn-palace\"")
                .contains("popcorn_holds_total{")
                .contains("popcorn_bookings_total{")
                .contains("popcorn_payments_total{")
                .contains("popcorn_outbox_pending_events")
                .contains("popcorn_outbox_oldest_event_age_seconds")
                .contains("popcorn_seatmap_streams")
                // Runtime metrics carry the label too, not only the request timings.
                .contains("jvm_memory_used_bytes{application=\"popcorn-palace\",")
                .contains("hikaricp_connections_active{application=\"popcorn-palace\",");
    }

    @DisplayName("Every response names its trace; error responses repeat the id in the body")
    @Test
    void responsesNameTheirTrace()
    {
        Response ok = get(api("/api/v1/movies"));
        assertThat(ok.traceId()).matches("[0-9a-f]{32}");

        Response notFound = get(api("/api/v1/movies/999"));
        assertThat(notFound.status()).isEqualTo(404);
        assertThat(notFound.traceId()).matches("[0-9a-f]{32}").isNotEqualTo(ok.traceId());
        assertThat(JsonPath.<String>read(notFound.body(), "$.traceId")).isEqualTo(notFound.traceId());
    }

    @DisplayName("Info names the application")
    @Test
    void infoNamesTheApplication()
    {
        Response info = get(management("/actuator/info"));
        assertThat(info.status()).isEqualTo(200);
        assertThat(JsonPath.<String>read(info.body(), "$.app.name")).isEqualTo("Popcorn Palace API");
    }

    // ------------------------------------------------------------ helpers

    private String api(String path)
    {
        return "http://localhost:" + apiPort + path;
    }

    private String management(String path)
    {
        return "http://localhost:" + managementPort + path;
    }

    private Response get(String url)
    {
        return http.get().uri(url).exchange((request, response) -> new Response(
                response.getStatusCode().value(),
                new String(response.getBody().readAllBytes()),
                response.getHeaders().getFirst(TraceIdFilter.HEADER)));
    }
}
