-- Reserves several seats for one customer, all or nothing.
--
-- Redis runs a script as a single uninterruptible step, so no other request
-- can slip in between "are these seats free?" and "take them". That is what
-- makes holding several seats at once safe without any locking in the application.
--
-- KEYS[1]   key marking that this user has a hold for this showtime
-- KEYS[2]   key of the hold record
-- KEYS[3..] one key per requested seat
-- ARGV[1]   time to live in milliseconds
-- ARGV[2]   hold id
-- ARGV[3]   hold record (JSON)
--
-- Returns 'OK', or 'USER:<hold id>' when the user already has a hold for this
-- showtime, or 'TAKEN:<i,j,..>' with the 1-based positions of the seats that
-- are already held. On any result other than 'OK' nothing is changed.

local existingHold = redis.call('GET', KEYS[1])
if existingHold then
    return 'USER:' .. existingHold
end

local taken = {}
for i = 3, #KEYS do
    if redis.call('EXISTS', KEYS[i]) == 1 then
        taken[#taken + 1] = tostring(i - 2)
    end
end
if #taken > 0 then
    return 'TAKEN:' .. table.concat(taken, ',')
end

for i = 3, #KEYS do
    redis.call('SET', KEYS[i], ARGV[2], 'PX', ARGV[1])
end
redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[1])
redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[1])
return 'OK'
