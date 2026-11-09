-- Deletes a key only if it still holds the expected value.
--
-- Used to release a lock: the lock has a time to live, so by the time its
-- owner releases it, it may have expired and been taken by someone else.
-- A plain DEL would then remove the other owner's lock.
--
-- KEYS[1]  the key
-- ARGV[1]  the value the caller put there

if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
