-- Fixed-window counter: counts one request and returns the count together with
-- the time left in the current window. Incrementing and starting the window's
-- timer happen in one step, so a counter can never be left without an expiry.
--
-- KEYS[1]  counter key
-- ARGV[1]  window length in milliseconds
--
-- Returns { requests in this window, milliseconds until the window ends }

local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return { count, redis.call('PTTL', KEYS[1]) }
