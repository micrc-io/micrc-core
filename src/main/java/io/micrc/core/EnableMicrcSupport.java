package io.micrc.core;

import io.micrc.core._camel.CamelComponentTempConfiguration;
import io.micrc.core.application.EnableApplicationService;
import io.micrc.core.cache.springboot.CacheAutoConfiguration;
import io.micrc.core.configuration.springboot.EmbeddedServerLoggingApplicationRunner;
import io.micrc.core.integration.EnableIntegration;
import io.micrc.core.message.springboot.ClassPathMessageScannerRegistrar;
import io.micrc.core.message.springboot.MessageAutoConfiguration;
import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;
import io.micrc.core.rpc.springboot.ClassPathRestEndpointScannerRegistrar;
import io.micrc.core.rpc.springboot.MockServerAutoConfiguration;
import io.micrc.core.rpc.springboot.RpcAutoConfiguration;
import io.micrc.core.rpc.springboot.RpcMockServerScannerRegistrar;
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
    CamelComponentTempConfiguration.class,
    EmbeddedServerLoggingApplicationRunner.class,
    PersistenceAutoConfiguration.class,
    CacheAutoConfiguration.class,
    RpcAutoConfiguration.class,
    ClassPathRestEndpointScannerRegistrar.class,
    RpcMockServerScannerRegistrar.class,
    MockServerAutoConfiguration.class,
    MessageAutoConfiguration.class,
    ClassPathMessageScannerRegistrar.class,
    ScheduleAutoConfiguration.class,
})
public @interface EnableMicrcSupport {
    String[] basePackages() default {};
}
