package io.micrc.core.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micrc.core.application.businesses.EnableBusinessesService;

/**
 * 整体应用服务支持启动注解，用于客户端程序便捷启动业务、展示、衍生服务支持
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-23 21:02
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EnableBusinessesService
public @interface EnableApplicationService {
    String[] servicePackages() default {};
}
