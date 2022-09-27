package io.micrc.core.cache.layering;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * 分级缓存管理器
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-26 18:18
 */
@Slf4j
public class CaffeineRedisCacheManager implements CacheManager {

    private ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();
    private CaffeineRedisCacheConfiguration cacheConfiguration;
    private CustomizeRedisCache redisCache;

    public CaffeineRedisCacheManager(CaffeineRedisCacheConfiguration cacheConfiguration,
                                     CustomizeRedisCache redisCache) {
        this.cacheConfiguration = cacheConfiguration;
        this.redisCache = redisCache;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = caches.get(name);
        if (cache != null) return cache;
        cache = new CaffeineRedisCache(name, redisCache, caffeineCache(), cacheConfiguration);
        Cache old = caches.putIfAbsent(name, cache);
        log.info("Create cache: {}", name);
        return old == null ? cache : old;
    }

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache() {
        return Caffeine.newBuilder()
            .expireAfterAccess(cacheConfiguration.getCaffeine().getExpireAfterAccess(), TimeUnit.SECONDS)
            .expireAfterWrite(cacheConfiguration.getCaffeine().getExpireAfterWrite(), TimeUnit.SECONDS)
            .initialCapacity(cacheConfiguration.getCaffeine().getInitialCapacity())
            .maximumSize(cacheConfiguration.getCaffeine().getMaximumSize())
            .build();
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.caches.keySet());
    }

    public void clearLocal(String cacheName, Object key) {
        Cache cache = caches.get(cacheName);
        if(cache == null) {
            return;
        }
        CaffeineRedisCache caffeineRedisCache = (CaffeineRedisCache) cache;
        caffeineRedisCache.clearLocal(key);
    }
}
