package io.micrc.core.integration.command.businesses;

import io.micrc.core.integration.command.businesses.springboot.ClassPathCommandAdapterScannerRegistrar;
import io.micrc.core.integration.command.businesses.springboot.CommandAdapterAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 业务服务启动注解，用于客户端程序启用业务服务支持
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({ClassPathCommandAdapterScannerRegistrar.class, CommandAdapterAutoConfiguration.class})
public @interface EnableCommandAdapter {
    String[] servicePackages() default {};
}
