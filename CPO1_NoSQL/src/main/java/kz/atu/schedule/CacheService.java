package kz.atu.schedule;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import java.util.Set;

public class CacheService {
    private final JedisPooled jedis;

    public CacheService(String host, int port) { this(host, port, 0); }

    public CacheService(String host, int port, int database) {
        jedis = new JedisPooled(new HostAndPort(host, port),
                DefaultJedisClientConfig.builder()
                        .database(database)
                        .connectionTimeoutMillis(500)
                        .socketTimeoutMillis(1000)
                        .build());
    }

    // Если Redis недоступен, методы не бросают исключение: приложение читает из MongoDB.
    public String get(String key) {
        try { return jedis.get(key); } catch (Exception e) { return null; }
    }
    public void setex(String key, int ttl, String value) {
        try { jedis.setex(key, ttl, value); } catch (Exception ignored) {}
    }
    public void sadd(String tag, String key) {
        try { jedis.sadd(tag, key); } catch (Exception ignored) {}
    }
    public void incr(String counter) {
        try { jedis.incr(counter); } catch (Exception ignored) {}
    }
    public long counter(String name) {
        try { String v = jedis.get(name); return v == null ? 0 : Long.parseLong(v); }
        catch (Exception e) { return 0; }
    }
    public boolean exists(String key) {
        try { return jedis.exists(key); } catch (Exception e) { return false; }
    }
    public long ttl(String key) {
        try { return jedis.ttl(key); } catch (Exception e) { return -2; }
    }
    /** Удаляет все ключи, привязанные к тегу, и сам тег. Возвращает число удалённых ключей. */
    public int invalidateTag(String tag) {
        try {
            Set<String> keys = jedis.smembers(tag);
            if (!keys.isEmpty()) jedis.del(keys.toArray(new String[0]));
            jedis.del(tag);
            return keys.size();
        } catch (Exception e) { return 0; }
    }
    public void flushAll() {
        try { jedis.flushDB(); } catch (Exception ignored) {}
    }
}