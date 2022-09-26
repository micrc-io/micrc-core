package io.micrc.core.cache.layering;

import java.util.Map;
import java.util.concurrent.Callable;

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
    private static final long DEFAULT_EXPIRATION = 0;

    private String name;
    private Cache<String, Object> caffeineCache;
    private CustomizeRedisCache redisCache;

    protected CaffeineRedisCache(boolean allowNullValues) {
        super(allowNullValues);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return null;
    }

    @Override
    public void put(Object key, Object value) {
        // TODO Auto-generated method stub
        
    }
    @Override
    public void evict(Object key) {
        // TODO Auto-generated method stub
        
    }
    @Override
    public void clear() {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected Object lookup(Object key) {
        return null;
    }

    private String getKey(Object key) {
        return this.name.concat(":").concat((String) key);
    }
    private long getExpire() {
		// Long cacheNameExpire = expires.get(this.cacheName);
		// return cacheNameExpire == null ? expire : cacheNameExpire.longValue();
        return DEFAULT_EXPIRATION;
    }

}
