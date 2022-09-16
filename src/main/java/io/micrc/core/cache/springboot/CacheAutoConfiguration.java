package io.micrc.core.cache.springboot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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

    // @Autowired
    // private RedisProperties redisProperties; // 应该不用注入自建，自动配置创建集群环境下的RedisConnectionFactory

    @Profile({ "default", "local" })
    @Bean(destroyMethod = "stop")
    public RedisServer redisMockServer() {
        return RedisServer.builder().port(6370).build();
    }

    @Profile({ "default", "local" })
    @Bean(destroyMethod = "destroy")
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6370);
    }
}
