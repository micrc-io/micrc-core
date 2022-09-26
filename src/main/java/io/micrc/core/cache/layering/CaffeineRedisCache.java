package io.micrc.core.cache.layering;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.cache.support.AbstractValueAdaptingCache;

import com.github.benmanes.caffeine.cache.Cache;

import lombok.extern.slf4j.Slf4j;

/**
 * 分级缓存
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-26 18:26
 */
@Slf4j
public class CaffeineRedisCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache<Object, Object> caffeineCache;
    private final CustomizeRedisCache redisCache;
    private final CaffeineRedisCacheConfiguration cacheConfiguration;

    private Map<String, ReentrantLock> keyLockMap = new ConcurrentHashMap<>();

    public CaffeineRedisCache(String cacheName, CustomizeRedisCache redisCache,
                              Cache<Object, Object> caffeineCache,
                              CaffeineRedisCacheConfiguration cacheConfiguration) {
        super(true);
        this.name = cacheName;
        this.redisCache = redisCache;
        this.caffeineCache = caffeineCache;
        this.cacheConfiguration = cacheConfiguration;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = lookup(key);
        if (value != null) return (T) value;
        ReentrantLock lock = keyLockMap.get(key.toString());
        if (lock == null) {
            lock = new ReentrantLock();
            keyLockMap.putIfAbsent(key.toString(), lock);

        }
        try {
            lock.lock();
            value = lookup(key);
            if (value != null) return (T) value;
            value = valueLoader.call();
            Object storeValue = toStoreValue(value);
            put(key, storeValue);
            return (T) value;
        } catch(Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e.getCause());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (!super.isAllowNullValues() && value == null) {
            this.evict(key);
            return;
        }
        redisCache.set(getKey(key), toStoreValue(value), cacheConfiguration.getRedis().getDefaultExpire());
        pushInvalid(key);
        caffeineCache.put(key, toStoreValue(value));
    }

    @Override
    public synchronized ValueWrapper putIfAbsent(Object key, Object value) {
        Object old = redisCache.get(getKey(key));
        if (old == null) {
            redisCache.set(getKey(key), toStoreValue(value), cacheConfiguration.getRedis().getDefaultExpire());
            pushInvalid(key);
            caffeineCache.put(key, toStoreValue(value));
        }
        return toValueWrapper(old);
    }

    @Override
    public void evict(Object key) {
        redisCache.delete(getKey(key));
        pushInvalid(key);
        caffeineCache.invalidate(key);
    }
    @Override
    public void clear() {
        Set<Object> keys = redisCache.keys(this.name.concat(":*"));
        keys.forEach(redisCache::delete);
        pushInvalid(null);
        caffeineCache.invalidateAll();
    }

    @Override
    protected Object lookup(Object key) {
        Object value = caffeineCache.getIfPresent(key);
        if (value != null) {
            log.debug("Caffeine cache hit. CacheName: {}, Key: {}, Value: {}", this.getName(), key, value);
            return value;
        }
        value = redisCache.get(getKey(key));
        if (value != null) {
            log.debug("Redis cache hit, and put in caffeine cache. CacheName: {}, Key: {}, Value: {}",
                this.getName(), key, value);
            caffeineCache.put(key, toStoreValue(value));
        }
        return value;
    }

    public void clearLocal(Object key) {
        log.debug("Clear local cache: {}", key);
        if (key == null) {
            caffeineCache.invalidateAll();
            return;
        }
        caffeineCache.invalidate(key);
    }

    private String getKey(Object key) {
        return this.name.concat(":").concat((String) key);
    }

    private void pushInvalid(Object key) {
        String keyStr = key == null ? null : (String) key;
        redisCache.getRedisTemplate().convertAndSend(
            cacheConfiguration.getRedis().getInvalidMessageChannel(),
            new CacheInvalidSynchronizer.CacheMessage<String>(this.name, keyStr));
    }
}
