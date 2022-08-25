package io.micrc.core.application.businesses;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务服务启动注解，用于客户端程序启用业务服务支持
 *
 * @author weiguan
 * @version 0.0.1
 * @date 2022-08-23 21:02
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface EnableBusinessesService {
    String[] servicePackages() default {};
}
