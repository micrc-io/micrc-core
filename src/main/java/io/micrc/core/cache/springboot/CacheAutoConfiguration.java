package io.micrc.core.cache.springboot;

import org.springframework.cache.annotation.EnableCaching;
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
@EnableCaching
public class CacheAutoConfiguration {

    // @Autowired
    // private RedisProperties redisProperties; // 应该不用注入自建，自动配置创建集群环境下的RedisConnectionFactory
    // 当非default，local环境时，会使用configmap和secret配置redis cluster连接工厂，也不再启动redis mock server
    // spring cache在springboot自动配置下会使用redis作为cache provider
    // shedlock redis provider也会使用这个生产环境的redis连接工厂

    @Profile({ "default", "local" })
    @Bean(initMethod = "start", destroyMethod = "stop")
    public RedisServer redisMockServer() {
        return RedisServer.builder().port(6370).build();
    }

    @Profile({ "default", "local" })
    @Bean(destroyMethod = "destroy")
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6370);
    }
}
