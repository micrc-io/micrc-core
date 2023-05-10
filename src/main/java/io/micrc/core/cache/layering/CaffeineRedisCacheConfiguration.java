package io.micrc.core.cache.layering;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 分级缓存配置
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-26 22:59
 */
@Data
@ConfigurationProperties(prefix = "micrc.cache.layering")
public class CaffeineRedisCacheConfiguration {

    private CaffeineCacheConfiguration caffeine = new CaffeineCacheConfiguration();
    private RedisCacheConfiguration redis = new RedisCacheConfiguration();
    
    @Data
    public static class CaffeineCacheConfiguration {
        private int initialCapacity = 1000;
        private long maximumSize = 3000;
        private long expireAfterAccess = 1800;
        private long expireAfterWrite = 3600;
        // private long refreshAfterWrite = 300; // TODO ?
    }

    @Data
    public static class RedisCacheConfiguration {
        private long defaultExpire = 0;
        private String invalidMessageChannel = "cache:layering:topic";
    }
}
