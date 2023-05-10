package io.micrc.core.cache.layering;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * 自定义redis cache实现
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-26 18:10
 */
public class CustomizeRedisCache {
    
    private RedisTemplate<Object, Object> redisTemplate;

    public Object get(Object key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void set(Object key, Object value, long timeout) {
        if (timeout > 0) {
            redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.MILLISECONDS);
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
    }

    public void delete(Object key) {
        redisTemplate.delete(key);
    }

    public Set<Object> keys(Object key) {
        return new HashSet<>(redisTemplate.keys(key + "*"));
    }

    public RedisTemplate<Object, Object> getRedisTemplate() {
        return this.redisTemplate;
    }

    public void setRedisTemplate(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
