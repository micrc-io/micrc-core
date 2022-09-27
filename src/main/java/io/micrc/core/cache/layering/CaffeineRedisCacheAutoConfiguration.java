package io.micrc.core.cache.layering;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(CaffeineRedisCacheConfiguration.class)
public class CaffeineRedisCacheAutoConfiguration {
    @Autowired
    private CaffeineRedisCacheConfiguration properties;

    @Bean
    @ConditionalOnMissingBean(name = "stringKeyRedisTemplate")
    public RedisTemplate<Object, Object> stringKeyRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        return template;
    }

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    public CustomizeRedisCache customizeRedisCache(RedisTemplate<Object, Object> stringKeyRedisTemplate) {
        CustomizeRedisCache cache = new CustomizeRedisCache();
        cache.setRedisTemplate(stringKeyRedisTemplate);
        return cache;
    }

    @Bean
    @ConditionalOnClass(CustomizeRedisCache.class)
    public CaffeineRedisCacheManager caffeineRedisCacheManager(CustomizeRedisCache customizeRedisCache) {
        return new CaffeineRedisCacheManager(properties, customizeRedisCache);
    }

    @Bean
    @ConditionalOnClass(CustomizeRedisCache.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            CustomizeRedisCache customizeRedisCache,
            CaffeineRedisCacheManager caffeineRedisCacheManager,
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        CacheInvalidSynchronizer synchronizer = new CacheInvalidSynchronizer(
            customizeRedisCache, caffeineRedisCacheManager);
        listenerContainer.addMessageListener(
            synchronizer, new ChannelTopic(properties.getRedis().getInvalidMessageChannel()));
        return listenerContainer;
    }
}
