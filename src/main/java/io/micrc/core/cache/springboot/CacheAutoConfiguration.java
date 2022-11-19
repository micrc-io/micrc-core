package io.micrc.core.cache.springboot;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micrc.core.cache.layering.CacheInvalidSynchronizer;
import io.micrc.core.cache.layering.CaffeineRedisCacheConfiguration;
import io.micrc.core.cache.layering.CaffeineRedisCacheManager;
import io.micrc.core.cache.layering.CustomizeRedisCache;

/**
 * cache auto configuration，mock redis server, lettuce redis client
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-15 04:31
 */
@Configuration
@EnableCaching
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(CaffeineRedisCacheConfiguration.class)
public class CacheAutoConfiguration {

    @Autowired
    Environment environment;

    @Autowired
    private CaffeineRedisCacheConfiguration properties;

    @Primary
    @Bean
    public CaffeineCacheManager caffeineCacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(caffeine);
        return manager;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        return RedisCacheManagerBuilder.fromConnectionFactory(redisConnectionFactory)
            .cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig()
                    .prefixCacheNameWith(environment.getProperty("spring.application.name") + ":")
                    .entryTtl(Duration.ofMinutes(60))
            )
            .build();
    }

    @Bean
    public CaffeineRedisCacheManager caffeineRedisCacheManager(CustomizeRedisCache customizeRedisCache) {
        return new CaffeineRedisCacheManager(properties, customizeRedisCache);
    }

    @Bean
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> valueSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance, DefaultTyping.NON_FINAL, As.WRAPPER_ARRAY);
        valueSerializer.setObjectMapper(objectMapper);
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public Caffeine<Object, Object> caffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .initialCapacity(1000)
            .maximumSize(3000);
    }

    @Bean
    public CustomizeRedisCache customizeRedisCache(RedisTemplate<Object, Object> redisTemplate) {
        CustomizeRedisCache cache = new CustomizeRedisCache();
        cache.setRedisTemplate(redisTemplate);
        return cache;
    }

    @Bean
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

    @Bean
    public CacheResolver caffeineRepositoryCacheResolver(CaffeineCacheManager caffeineCacheManager) {
        return new RepositoryCacheResolver(caffeineCacheManager);
    }

    @Bean
    public CacheResolver redisRepositoryCacheResolver(RedisCacheManager redisCacheManager) {
        return new RepositoryCacheResolver(redisCacheManager);
    }

    @Bean
    public CacheResolver caffeineRedisRepositoryCacheResolver(CaffeineRedisCacheManager caffeineRedisCacheManager) {
        return new RepositoryCacheResolver(caffeineRedisCacheManager);
    }

    @Bean
    public KeyGenerator entityIdKeyGenerator() {
        return new EntityIdKeyGenerator();
    }

    @Bean
    public KeyGenerator repositoryQueryKeyGenerator() {
        return new RepositoryQueryKeyGenerator();
    }
}
