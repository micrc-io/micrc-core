package io.micrc.core.integration.camel;

import io.micrc.core.integration.camel.springboot.CamelRouteAdapterAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({CamelRouteAdapterAutoConfiguration.class})
public @interface EnableCamelRouteAdapter {
    String[] servicePackages() default {};
}
