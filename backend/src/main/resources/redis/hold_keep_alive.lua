-- Called right before a hold is turned into a booking. Confirms that the hold
-- is intact (the record exists and every seat is still held by it) and makes
-- sure it will not expire in the middle of the database transaction.
--
-- KEYS[1]   key marking that this user has a hold for this showtime
-- KEYS[2]   key of the hold record
-- KEYS[3..] one key per seat of the hold
-- ARGV[1]   hold id
-- ARGV[2]   minimum remaining time to live in milliseconds
--
-- Returns 1 when the hold is intact, 0 when it has expired or lost a seat.

if redis.call('EXISTS', KEYS[2]) == 0 then
    return 0
end
for i = 3, #KEYS do
    if redis.call('GET', KEYS[i]) ~= ARGV[1] then
        return 0
    end
end

local minimum = tonumber(ARGV[2])
for i = 1, #KEYS do
    local remaining = redis.call('PTTL', KEYS[i])
    if remaining >= 0 and remaining < minimum then
        redis.call('PEXPIRE', KEYS[i], minimum)
    end
end
return 1
