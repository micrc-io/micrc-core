package io.micrc.core.integration.derivations;

import io.micrc.core.integration.derivations.springboot.ClassPathDerivationsAdapterScannerRegistrar;
import io.micrc.core.integration.derivations.springboot.DerivationsAdapterAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 衍生适配器启动注解，用于衍生逻辑处理支持
 *
 * @author hyosunghan
 * @date 2022-09-21 13:58
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({ClassPathDerivationsAdapterScannerRegistrar.class, DerivationsAdapterAutoConfiguration.class})
public @interface EnableDerivationsAdapter {
    String[] servicePackages() default {};
}
