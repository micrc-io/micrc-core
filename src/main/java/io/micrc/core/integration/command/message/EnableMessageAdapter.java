package io.micrc.core.integration.command.message;

import io.micrc.core.integration.command.message.springboot.ClassPathMessageAdapterScannerRegistrar;
import io.micrc.core.integration.command.message.springboot.MessageAdapterAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 消息接收适配器启动注解，用于客户端程序启用消息接收支持
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({ClassPathMessageAdapterScannerRegistrar.class, MessageAdapterAutoConfiguration.class})
public @interface EnableMessageAdapter {
    String[] servicePackages() default {};
}
