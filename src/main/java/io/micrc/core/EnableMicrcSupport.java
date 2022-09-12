package io.micrc.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import io.micrc.core.application.EnableApplicationService;
import io.micrc.core.integration.EnableIntegration;
import io.micrc.core.message.springboot.ClassPathMessageSubscriberScannerRegistrar;
import io.micrc.core.message.springboot.MessageAutoConfiguration;
import io.micrc.core.message.springboot.MessageMockSenderApiScannerRegistrar;
import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;
import io.micrc.core.rpc.springboot.ClassPathRestEndpointScannerRegistrar;
import io.micrc.core.rpc.springboot.RpcAutoConfiguration;
import io.micrc.core.rpc.springboot.RpcMockServerScanner;

/**
 * micrc core support on-off switch
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-23 20:31
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EnableApplicationService
@EnableIntegration
@Import({
    PersistenceAutoConfiguration.class,
    RpcAutoConfiguration.class,
    ClassPathRestEndpointScannerRegistrar.class,
    RpcMockServerScanner.class,
    MessageAutoConfiguration.class,
    ClassPathMessageSubscriberScannerRegistrar.class,
    MessageMockSenderApiScannerRegistrar.class
})
public @interface EnableMicrcSupport {
    String[] basePackages() default {};
}
