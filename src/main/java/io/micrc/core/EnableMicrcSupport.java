package io.micrc.core;

import io.micrc.core._camel.CamelComponentTempConfiguration;
import io.micrc.core.application.EnableApplicationService;
import io.micrc.core.cache.springboot.CacheAutoConfiguration;
import io.micrc.core.integration.EnableIntegration;
import io.micrc.core.message.springboot.ClassPathMessageSubscriberScannerRegistrar;
import io.micrc.core.message.springboot.MessageAutoConfiguration;
import io.micrc.core.message.springboot.MessageMockSenderApiScannerRegistrar;
import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;
import io.micrc.core.rpc.springboot.ClassPathRestEndpointScannerRegistrar;
import io.micrc.core.rpc.springboot.RpcAutoConfiguration;
import io.micrc.core.rpc.springboot.RpcMockServerScanner;
import org.springframework.context.annotation.Import;

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
@EnableApplicationService
@EnableIntegration
@Import({
        CamelComponentTempConfiguration.class,
        PersistenceAutoConfiguration.class,
        RpcAutoConfiguration.class,
        ClassPathRestEndpointScannerRegistrar.class,
        RpcMockServerScanner.class,
        MessageAutoConfiguration.class,
        ClassPathMessageSubscriberScannerRegistrar.class,
        MessageMockSenderApiScannerRegistrar.class,
        CacheAutoConfiguration.class,
        CamelComponentTempConfiguration.class

})
public @interface EnableMicrcSupport {
    String[] basePackages() default {};
}
