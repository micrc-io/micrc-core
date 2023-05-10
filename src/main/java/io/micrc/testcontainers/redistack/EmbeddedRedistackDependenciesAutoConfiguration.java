package io.micrc.testcontainers.redistack;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.playtika.test.common.spring.DependsOnPostProcessor;

/**
 * dependencies auto configuration
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-01 21:33
 */
@Configuration
@AutoConfigureOrder
@ConditionalOnExpression("${embedded.containers.enabled:true}")
@ConditionalOnProperty(name = "embedded.redistack.enabled", matchIfMissing = true)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@SuppressWarnings("all")
public class EmbeddedRedistackDependenciesAutoConfiguration {

    @Configuration
    @ConditionalOnClass(RedisConnectionFactory.class)
    public static class RedistackConnectionFactoryDependencyContext {
        @Bean
        public static BeanFactoryPostProcessor redistackConnectionFactoryDependencyPostProcessor() {
            return new DependsOnPostProcessor(
                RedisConnectionFactory.class, new String[] { RedistackProperties.BEAN_NAME_EMBEDDED_REDIS_STACK });
        }
    }

    @Configuration
    @ConditionalOnClass(RedisTemplate.class)
    public static class RedistackTemplateDependencyContext {
        @Bean
        public static BeanFactoryPostProcessor redistackTemplateDependencyPostProcessor() {
            return new DependsOnPostProcessor(
                RedisTemplate.class, new String[] { RedistackProperties.BEAN_NAME_EMBEDDED_REDIS_STACK });
        }
    }
}
