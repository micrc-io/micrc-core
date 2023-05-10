package io.micrc.core.integration.runner;


import io.micrc.core.integration.runner.springboot.ClassPathRunnerAdapterScannerRegistrar;
import io.micrc.core.integration.runner.springboot.RunnerAdapterAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 程序启动适配器注解，用与客户端启用程序启动器接收
 *
 * @author xwyang
 * @date 2023-04-08 16:25
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({ClassPathRunnerAdapterScannerRegistrar.class, RunnerAdapterAutoConfiguration.class})
public @interface EnableRunnerAdapter {

    String[] servicePackages() default {};
}
