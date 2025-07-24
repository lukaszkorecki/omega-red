-- Args:
-- KEYS[i] - lock name
-- ARGV[1] - lock ID
-- check if we're the lock-holder by checking if lock-id exists in the lock hash
if (redis.call('hexists', KEYS[1], ARGV[1]) == 0) then
   -- nope, we bail
   return nil;
end;

-- looks like we are the 'owner', we delete the lock-id key to 'release' the lock
redis.call('hdel', KEYS[1], ARGV[1]);
return 1;
