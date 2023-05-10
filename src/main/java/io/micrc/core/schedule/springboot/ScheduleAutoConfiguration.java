package io.micrc.core.schedule.springboot;

import org.apache.camel.component.direct.DirectComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.micrc.core.schedule.ScheduleRouterExecution;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * schedule autoconfiguration. enable spring scheduling, shedlock redis provider
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-18 21:31
 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableSchedulerLock(defaultLockAtMostFor = "10m", order = Ordered.HIGHEST_PRECEDENCE)
@Import({ ScheduleRouterExecution.class })
public class ScheduleAutoConfiguration {

    @Value("${micrc.key}")
    private String keyPrefix;
    @Value("${spring.application.name}")
    private String appName;

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider.Builder(connectionFactory)
            .keyPrefix(keyPrefix)
            .environment(appName)
            .build();
    }

    @Bean("schedule")
    public DirectComponent schedule() {
        return new DirectComponent();
    }
}
