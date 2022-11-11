package io.micrc.core.application.presentations;

import io.micrc.core.application.presentations.springboot.ClassPathPresentationsServiceScannerRegistrar;
import io.micrc.core.application.presentations.springboot.PresentationsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 展示服务启动注解
 *
 * @author hyosunghan
 * @since 1.0
 * @date 2022-09-01
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({
        ClassPathPresentationsServiceScannerRegistrar.class,
        PresentationsServiceAutoConfiguration.class,
        ApplicationPresentationsServiceRouteConfiguration.class,
        PresentationsServiceRouterExecution.class})
public @interface EnablePresentationsService {

    /**
     * 扫描包
     *
     * @return
     */
    String[] servicePackages() default {};
}
