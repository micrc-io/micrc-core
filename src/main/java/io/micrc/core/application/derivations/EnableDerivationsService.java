package io.micrc.core.application.derivations;

import io.micrc.core.application.derivations.springboot.ClassPathDerivationsServiceScannerRegistrar;
import io.micrc.core.application.derivations.springboot.DerivationsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 衍生服务启动注解
 *
 * @author hyosunghan
 * @date 2022-09-17 11:59
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({
        ClassPathDerivationsServiceScannerRegistrar.class,
        DerivationsServiceAutoConfiguration.class,
        ApplicationDerivationsServiceRouteConfiguration.class,
        DerivationsServiceRouterExecution.class
})
public @interface EnableDerivationsService {

    /**
     * 扫描包
     *
     * @return
     */
    String[] servicePackages() default {};
}
