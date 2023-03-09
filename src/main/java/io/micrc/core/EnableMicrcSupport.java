package io.micrc.core;

import io.micrc.core._camel.springboot.CustomCamelComponentAutoConfiguration;
import io.micrc.core.application.EnableApplicationService;
import io.micrc.core.cache.springboot.CacheAutoConfiguration;
import io.micrc.core.configuration.springboot.EmbeddedServerLoggingApplicationRunner;
import io.micrc.core.dynamic.springboot.DynamicExecutorAutoConfiguration;
import io.micrc.core.integration.EnableIntegration;
import io.micrc.core.message.MessageMockSenderRouteConfiguration;
import io.micrc.core.message.springboot.MessageMockSenderApiScannerRegistrar;
import io.micrc.core.message.springboot.ClassPathMessageScannerRegistrar;
import io.micrc.core.message.springboot.MessageAutoConfiguration;
import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;
import io.micrc.core.rpc.springboot.ClassPathRestEndpointScannerRegistrar;
import io.micrc.core.rpc.springboot.IntegrationInfoScannerRegistrar;
import io.micrc.core.rpc.springboot.RpcAutoConfiguration;
import io.micrc.core.rpc.springboot.RpcMockServerAutoConfiguration;
import io.micrc.core.schedule.springboot.ScheduleAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.lang.annotation.*;

/**
 * micrc core support on-off switch
 *
 * @author weiguan
 * @date 2022-08-23 20:31
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EntityScan
@EnableJpaRepositories
@EnableCaching
@EnableApplicationService
@EnableIntegration
@Import({
        CustomCamelComponentAutoConfiguration.class,
        EmbeddedServerLoggingApplicationRunner.class,
        PersistenceAutoConfiguration.class,
        CacheAutoConfiguration.class,
        RpcAutoConfiguration.class,
        ClassPathRestEndpointScannerRegistrar.class,
        IntegrationInfoScannerRegistrar.class,
        RpcMockServerAutoConfiguration.class,
        MessageMockSenderApiScannerRegistrar.class,
        MessageMockSenderRouteConfiguration.class,
        ScheduleAutoConfiguration.class,
        DynamicExecutorAutoConfiguration.class,
        ClassPathMessageScannerRegistrar.class,
        MessageAutoConfiguration.class
})
public @interface EnableMicrcSupport {
    String[] basePackages() default {};
}
