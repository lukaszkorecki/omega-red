
-- Args:
-- 1. lock-key: the key to lock on (KEYS[1])
-- 2. lock-id: the unique identifier for this lock holder (ARGV[1])
-- check if we're the lock-holder by checking if lock-id exists in the lock hash
if (redis.call('hexists', KEYS[1], ARGV[1]) == 0) then
   -- nope, we bail
    return nil;
  end;

  -- looks like we are the 'owner'
  local counter = redis.call('hincrby', KEYS[1], ARGV[1], -1);
  if (counter > 0) then
     redis.call('pexpire', KEYS[1], ARGV[2]);
     return counter;
  else
     redis.call('del', KEYS[1]);
     return 0;
  end;
  return nil;
