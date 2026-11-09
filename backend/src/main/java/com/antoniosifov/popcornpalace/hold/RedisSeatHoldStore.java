package com.antoniosifov.popcornpalace.hold;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Seat holds as Redis keys with a time to live. For a showtime {@code 42}:
 *
 * <pre>
 * hold:{42}:seat:17      -> hold id     one key per held seat
 * hold:{42}:rec:&lt;hold id> -> JSON        the hold: owner, seats, prices
 * hold:{42}:user:&lt;user>   -> hold id     one hold per user per showtime
 * </pre>
 *
 * All three kinds of key expire together, so an abandoned hold frees its
 * seats without any cleanup job.
 *
 * <p>Changes that touch several keys run as Lua scripts (see
 * {@code src/main/resources/redis}), which Redis executes atomically. The
 * {@code {42}} part is a hash tag: it puts every key of a showtime in the
 * same slot, which is what Redis Cluster requires of keys used in one script.
 */
@Component
public class RedisSeatHoldStore implements SeatHoldStore
{
    private static final RedisScript<String> ACQUIRE =
            RedisScript.of(new ClassPathResource("redis/hold_acquire.lua"), String.class);
    private static final RedisScript<Long> RELEASE =
            RedisScript.of(new ClassPathResource("redis/hold_release.lua"), Long.class);
    private static final RedisScript<Long> KEEP_ALIVE =
            RedisScript.of(new ClassPathResource("redis/hold_keep_alive.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisSeatHoldStore(StringRedisTemplate redis, JsonMapper jsonMapper)
    {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public AcquireResult acquire(HoldRecord hold, Duration ttl)
    {
        String result = redis.execute(ACQUIRE, keysOf(hold),
                String.valueOf(ttl.toMillis()), hold.id().toString(), jsonMapper.writeValueAsString(hold));

        if ("OK".equals(result)) {
            return new AcquireResult.Acquired();
        }
        if (result != null && result.startsWith("USER:")) {
            return new AcquireResult.UserAlreadyHasHold(UUID.fromString(result.substring("USER:".length())));
        }
        if (result != null && result.startsWith("TAKEN:")) {
            // The script reports positions in the request; translate them back to seat ids.
            List<Long> requested = hold.seatIds();
            List<Long> taken = Arrays.stream(result.substring("TAKEN:".length()).split(","))
                    .map(position -> requested.get(Integer.parseInt(position) - 1))
                    .toList();
            return new AcquireResult.SeatsTaken(taken);
        }
        throw new IllegalStateException("Unexpected result from the hold script: " + result);
    }

    @Override
    public Optional<HoldRecord> find(long showtimeId, UUID holdId)
    {
        String json = redis.opsForValue().get(recordKey(showtimeId, holdId));
        return Optional.ofNullable(json).map(value -> jsonMapper.readValue(value, HoldRecord.class));
    }

    @Override
    public Optional<HoldRecord> findByUser(long showtimeId, UUID userId)
    {
        String holdId = redis.opsForValue().get(userKey(showtimeId, userId));
        return Optional.ofNullable(holdId).flatMap(id -> find(showtimeId, UUID.fromString(id)));
    }

    @Override
    public boolean keepAlive(HoldRecord hold, Duration atLeast)
    {
        Long intact = redis.execute(KEEP_ALIVE, keysOf(hold), hold.id().toString(), String.valueOf(atLeast.toMillis()));
        return Long.valueOf(1).equals(intact);
    }

    @Override
    public void release(HoldRecord hold)
    {
        redis.execute(RELEASE, keysOf(hold), hold.id().toString());
    }

    @Override
    public Map<Long, UUID> findHolders(long showtimeId, Collection<Long> seatIds)
    {
        if (seatIds.isEmpty()) {
            return Map.of();
        }
        // One round trip for the whole hall instead of one per seat.
        List<Long> ids = List.copyOf(seatIds);
        List<String> holdIds = redis.opsForValue().multiGet(ids.stream().map(id -> seatKey(showtimeId, id)).toList());

        Map<Long, UUID> holders = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            String holdId = holdIds == null ? null : holdIds.get(i);
            if (holdId != null) {
                holders.put(ids.get(i), UUID.fromString(holdId));
            }
        }
        return holders;
    }

    /** Key order expected by every hold script: user key, record key, then the seat keys. */
    private static List<String> keysOf(HoldRecord hold)
    {
        List<String> keys = new ArrayList<>();
        keys.add(userKey(hold.showtimeId(), hold.userId()));
        keys.add(recordKey(hold.showtimeId(), hold.id()));
        for (Long seatId : hold.seatIds()) {
            keys.add(seatKey(hold.showtimeId(), seatId));
        }
        return keys;
    }

    private static String prefix(long showtimeId)
    {
        return "hold:{" + showtimeId + "}";
    }

    static String seatKey(long showtimeId, long seatId)
    {
        return prefix(showtimeId) + ":seat:" + seatId;
    }

    static String recordKey(long showtimeId, UUID holdId)
    {
        return prefix(showtimeId) + ":rec:" + holdId;
    }

    static String userKey(long showtimeId, UUID userId)
    {
        return prefix(showtimeId) + ":user:" + userId;
    }
}
