package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.hall.SeatStatusChanged;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The open seat-map streams of this instance of the application: which
 * browsers are watching which showtime. {@link #broadcast} pushes a seat
 * change to all of them.
 *
 * <p>Server-sent events (SSE) are used rather than WebSockets because the
 * data flows one way only, server to browser, and browsers reconnect to an
 * SSE stream on their own.
 */
@Component
@EnableConfigurationProperties(SeatMapStreamProperties.class)
public class SeatMapBroadcaster
{
    /** Name of the SSE event that carries a {@link SeatStatusChanged}. */
    public static final String SEATS_EVENT = "seats";
    /** Sent once when a stream opens, so a client knows it is connected before anything changes. */
    public static final String CONNECTED_EVENT = "connected";

    private static final Logger log = LoggerFactory.getLogger(SeatMapBroadcaster.class);

    private final Map<Long, Set<SseEmitter>> streamsByShowtime = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;
    private final SeatMapStreamProperties properties;

    public SeatMapBroadcaster(JsonMapper jsonMapper, SeatMapStreamProperties properties, MeterRegistry registry)
    {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        Gauge.builder("popcorn.seatmap.streams", this, SeatMapBroadcaster::streamCount)
                .description("Browsers watching a live seat map on this instance")
                .register(registry);
    }

    /** Opens a stream for one showtime. The browser receives every change until it disconnects. */
    public SseEmitter subscribe(Long showtimeId)
    {
        SseEmitter stream = new SseEmitter(properties.streamTimeout().toMillis());
        Set<SseEmitter> streams = streamsByShowtime.computeIfAbsent(showtimeId, id -> new CopyOnWriteArraySet<>());
        streams.add(stream);

        Runnable forget = () -> streams.remove(stream);
        stream.onCompletion(forget);
        stream.onTimeout(forget);
        stream.onError(error -> forget.run());

        try {
            stream.send(SseEmitter.event().name(CONNECTED_EVENT).data("{\"showtimeId\": " + showtimeId + "}"));
        }
        catch (IOException | IllegalStateException exception) {
            forget.run();
            stream.completeWithError(exception);
        }
        return stream;
    }

    /** Sends the change to every stream of its showtime. A stream that cannot be written is dropped. */
    public void broadcast(SeatStatusChanged change)
    {
        Set<SseEmitter> streams = streamsByShowtime.get(change.showtimeId());
        if (streams == null || streams.isEmpty()) {
            return;
        }
        String json = jsonMapper.writeValueAsString(change);
        for (SseEmitter stream : streams) {
            try {
                stream.send(SseEmitter.event().name(SEATS_EVENT).data(json));
            }
            catch (IOException | IllegalStateException exception) {
                // The browser is gone; Spring will call the completion callbacks.
                streams.remove(stream);
                stream.completeWithError(exception);
            }
        }
    }

    /**
     * Keeps connections alive through proxies that close idle ones, and finds
     * browsers that went away without saying so (their write fails).
     */
    @Scheduled(fixedDelayString = "${app.seatmap.heartbeat-interval}")
    void heartbeat()
    {
        for (Set<SseEmitter> streams : streamsByShowtime.values()) {
            for (SseEmitter stream : streams) {
                try {
                    stream.send(SseEmitter.event().comment("keepalive"));
                }
                catch (IOException | IllegalStateException exception) {
                    streams.remove(stream);
                    stream.completeWithError(exception);
                }
            }
        }
    }

    /** How many browsers are watching the showtime right now, on this instance. */
    public int streamCount(Long showtimeId)
    {
        Set<SseEmitter> streams = streamsByShowtime.get(showtimeId);
        return streams == null ? 0 : streams.size();
    }

    /** How many browsers are watching any showtime right now, on this instance. */
    public int streamCount()
    {
        return streamsByShowtime.values().stream().mapToInt(Set::size).sum();
    }

    /** Closes every stream, so browsers reconnect (to another instance, during a deployment). */
    @PreDestroy
    public void disconnectAll()
    {
        int closed = 0;
        for (Set<SseEmitter> streams : streamsByShowtime.values()) {
            for (SseEmitter stream : streams) {
                stream.complete();
                closed++;
            }
            streams.clear();
        }
        if (closed > 0) {
            log.info("Closed {} seat map stream(s)", closed);
        }
    }
}
