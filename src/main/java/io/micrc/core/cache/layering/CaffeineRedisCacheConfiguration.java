package io.micrc.core.cache.layering;

import lombok.Data;

/**
 * 分级缓存配置
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-26 22:59
 */
@Data
public class CaffeineRedisCacheConfiguration {

    private CaffeineCacheConfiguration caffeine;
    private RedisCacheConfiguration redis;
    
    @Data
    public static class CaffeineCacheConfiguration {
        private int initialCapacity;
        private long maximumSize;
        private long expireAfterAccess;
        private long expireAfterWrite;
        private long refreshAfterWrite;
    }

    @Data
    public static class RedisCacheConfiguration {
        private long defaultExpire = 0;
        private String invalidMessageChannel = "cache:layering:topic";
    }
}
