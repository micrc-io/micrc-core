package io.micrc.core.application.businesses;

import io.micrc.core.application.businesses.springboot.BusinessesServiceAutoConfiguration;
import io.micrc.core.application.businesses.springboot.ClassPathBusinessesServiceScannerRegistrar;
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
@Import({ClassPathBusinessesServiceScannerRegistrar.class, BusinessesServiceAutoConfiguration.class})
public @interface EnableBusinessesService {
    String[] servicePackages() default {};
}
