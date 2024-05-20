package io.micrc.core.cache.springboot;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * cache 配置. redis connection, embedded redis
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-28 01:26
 */
public class CacheEnvironmentProcessor implements EnvironmentPostProcessor {
    private final Log log;

    public CacheEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        Properties properties = new Properties();
        bootstrapEmbeddedCache(profiles, properties, obtainProvider("CACHE", environment));
        PropertiesPropertySource source = new PropertiesPropertySource("micrc-cache", properties);
        environment.getPropertySources().addLast(source);
    }

    private void bootstrapEmbeddedCache(List<String> profiles, Properties properties, String provider) {
        if (!profiles.contains("default")) {
            properties.setProperty("embedded.redistack.enabled", "false");
        }
        // 目前redis-stack不支持cluster，也不使用客户端直连cluster的方式
        // default使用单机redis
        // dev，release(ack)集群环境中，额外部署redis-cluster-proxy对集群进行代理，云redis自带代理
        // 客户端连接方式保持一致，均为单机连接方式
        if (profiles.contains("default")) {
            log.info("Embedded redis server configuration for profile: 'default'");
            // embedded redis
            properties.setProperty("embedded.redistack.enabled", "true");
            properties.setProperty("embedded.redistack.reuseContainer", "true");
            properties.setProperty("embedded.redistack.dockerImage", "redis/redis-stack:6.2.4-v2");
            properties.setProperty("embedded.redistack.waitTimeoutInSeconds", "60");
            properties.setProperty("embedded.redistack.clustered", "false");
            properties.setProperty("embedded.redistack.requirepass", "true");

            properties.setProperty("micrc.spring.redis.httpPort", "${embedded.redistack.httpPort}");
        }
        if (profiles.contains("default")) {
            // default redis connection
            properties.setProperty("spring.redis.host", "${embedded.redistack.host}");
            properties.setProperty("spring.redis.port", "${embedded.redistack.port}");
            properties.setProperty("spring.redis.password", "${embedded.redistack.password}");
            properties.setProperty("spring.redis.username", "${embedded.redistack.username}");

        } else {
            // k8s集群中读取的configmap中的host，port和passwd
            properties.setProperty("spring.redis.host", "${" + provider + "_cache_host}");
            properties.setProperty("spring.redis.port", "${" + provider + "_cache_port}");
            properties.setProperty("spring.redis.password", "${" + provider + "_cache_password}");
            properties.setProperty("spring.redis.username", "${" + provider + "_cache_username}");
        }
        // 任何环境使用统一的连接配置
        properties.setProperty("spring.redis.database", "0");
        properties.setProperty("spring.redis.timeout", "3000");
        properties.setProperty("spring.redis.lettuce.pool.max-active", "1000");
        properties.setProperty("spring.redis.lettuce.pool.min-idle", "5");
        properties.setProperty("spring.redis.lettuce.pool.max-idle", "10");
        properties.setProperty("spring.redis.lettuce.pool.max-wait", "-1");
        properties.setProperty("spring.redis.pool.time-between-eviction-runs-millis", "2000");
    }

    private String obtainProvider(String middleware, ConfigurableEnvironment env) {
        String providersString = (String) env.getSystemEnvironment().getOrDefault(middleware + "_PROVIDERS", "");
        String[] providers = providersString.split(",");
        if (providers.length != 1) { // 配置错误，直接报错停止启动
            throw new RuntimeException();
        }
        return providers[0];
    }
}
