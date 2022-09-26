package io.micrc.core.cache.layering;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
        // cache = new CaffeineRedisCache(name, redisCache, caffeineCache(), cacheConfiguration);
        return null;
    }

    // private com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache() {
    //     return Caffeine.newBuilder().expireAfterAccess(duration, unit)
    // }

    @Override
    public Collection<String> getCacheNames() {
        // TODO Auto-generated method stub
        return null;
    }
    
}
