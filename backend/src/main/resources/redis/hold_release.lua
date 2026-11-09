-- Gives the seats of a hold back.
--
-- A seat key is deleted only if it still belongs to this hold. If the hold
-- has already expired and another customer now holds the seat, their key
-- must not be touched.
--
-- KEYS[1]   key marking that this user has a hold for this showtime
-- KEYS[2]   key of the hold record
-- KEYS[3..] one key per seat of the hold
-- ARGV[1]   hold id

for i = 3, #KEYS do
    if redis.call('GET', KEYS[i]) == ARGV[1] then
        redis.call('DEL', KEYS[i])
    end
end
if redis.call('GET', KEYS[1]) == ARGV[1] then
    redis.call('DEL', KEYS[1])
end
redis.call('DEL', KEYS[2])
return 1
