-- Args:
-- 1. lock-key: the key to lock on (KEYS[1]
-- 2. lock-id: the unique identifier for this lock holder (ARGV[1]))
-- 3. expiry-ms: how long to keep the lock (ARGV[2])

if (redis.call('exists', KEYS[1]) == 0) then
   redis.call('hset', KEYS[1], ARGV[1], 1);
   redis.call('pexpire', KEYS[1], ARGV[2]);
   return 1;
end;

if (redis.call('hexists', KEYS[1], ARGV[1]) == 1) then
   local counter = redis.call('hincrby', KEYS[1], ARGV[1], 1);
   redis.call('pexpire', KEYS[1], ARGV[2]);
   return counter;
end;
return nil;
