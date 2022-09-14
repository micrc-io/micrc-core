package io.micrc.core.cache.springboot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import redis.embedded.RedisServer;

/**
 * cache auto configuration，mock redis server, lettuce redis client
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-15 04:31
 */
@Configuration
public class CacheAutoConfiguration {
    
    @Profile({ "default", "local" })
    @Bean
    public RedisServer redisMockServer() {
        RedisServer server = new RedisServer(6370);
        server.start();
        return server;
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6370);
    }
}
