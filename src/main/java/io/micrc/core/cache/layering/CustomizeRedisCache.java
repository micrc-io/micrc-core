package io.micrc.core.cache.layering;

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

    

    public RedisTemplate<Object, Object> getRedisTemplate() {
        return this.redisTemplate;
    }

    public void setRedisTemplate(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
