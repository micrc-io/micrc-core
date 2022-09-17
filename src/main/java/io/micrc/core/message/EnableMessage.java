package io.micrc.core.message;

import io.micrc.core.message.springboot.ClassPathMessageScannerRegistrar;
import io.micrc.core.message.springboot.MessageAutoConfiguration;
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
@Import({
        ClassPathMessageScannerRegistrar.class,
        MessageAutoConfiguration.class
})
public @interface EnableMessage {
    String[] servicePackages() default {};
}
