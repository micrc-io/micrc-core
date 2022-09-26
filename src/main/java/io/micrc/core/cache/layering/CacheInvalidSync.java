package io.micrc.core.cache.layering;

import java.io.Serializable;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 缓存失效同步通知，使用redis pub/sub实现
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-26 18:05
 */
@Slf4j
@AllArgsConstructor
public class CacheInvalidSync implements MessageListener {

    private final CustomizeRedisCache redisCache;
    private final CaffeineRedisCacheManager cacheManager;

    @SuppressWarnings("unchecked")
    @Override
    public void onMessage(Message message, byte[] pattern) {
        CacheMessage<String> cacheMessage = (CacheMessage<String>) redisCache.getRedisTemplate()
            .getValueSerializer().deserialize(message.getBody());
        if (cacheMessage != null) {
            log.debug("Receive redis message. CacheName: {}; Key: {}. Clear local cache. ",
                cacheMessage.getCacheName(), cacheMessage.getKey());
            cacheManager.clearLocal(cacheMessage.getCacheName(), cacheMessage.getKey());
        }
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheMessage<T extends Serializable> implements Serializable {
        private String cacheName;
        private T key;
    }
}
