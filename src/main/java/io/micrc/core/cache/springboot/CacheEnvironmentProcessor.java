package io.micrc.core.cache.springboot;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

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
        bootstrapEmbeddedRedis(profiles, properties);
        PropertiesPropertySource source = new PropertiesPropertySource("micrc-cache", properties);
        environment.getPropertySources().addLast(source);
    }

    private void bootstrapEmbeddedRedis(List<String> profiles, Properties properties) {
        if (profiles.contains("default") || profiles.contains("local")) {
            log.info("Start embedded redis for env 'default' or 'local'. ");
            // embedded redis
            properties.setProperty("embedded.redis.enabled", "true");
            properties.setProperty("embedded.redis.reuseContainer", "true");
            properties.setProperty("embedded.redis.dockerImage", "redis:6.2");
            properties.setProperty("embedded.redis.waitTimeoutInSeconds", "60");
            properties.setProperty("embedded.redis.clustered", "false");
            properties.setProperty("embedded.redis.requirepass", "true");
            // redis connection
            properties.setProperty("spring.redis.host", "${embedded.redis.host}");
            properties.setProperty("spring.redis.port", "${embedded.redis.port}");
            // properties.setProperty("spring.redis.username", "${embedded.redis.user}"); // 当requirepass，不输入用户名
            properties.setProperty("spring.redis.password", "${embedded.redis.password}");
            properties.setProperty("spring.redis.timeout", "1000");
            properties.setProperty("spring.redis.lettuce.pool.max-active", "5");
            properties.setProperty("spring.redis.lettuce.pool.min-idle", "1");
            properties.setProperty("spring.redis.lettuce.pool.max-idle", "5");
            properties.setProperty("spring.redis.lettuce.pool.max-wait", "5000");
            properties.setProperty("spring.redis.pool.time-between-eviction-runs-millis", "2000");
        } else {
            properties.setProperty("embedded.redis.enabled", "false");
        }
    }
}
