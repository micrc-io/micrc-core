package io.micrc.core._camel.springboot;

import io.micrc.core._camel.CamelComponentTempConfiguration;
import io.micrc.core._camel.jit.JITDMNService;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * cache auto configuration，mock redis server, lettuce redis client
 *
 * @author weiguan
 * @date 2022-09-15 04:31
 * @since 0.0.1
 */
@Configuration
@Import({
        CamelComponentTempConfiguration.class,
        JITDMNService.class
})
public class CustomCamelComponentAutoConfiguration {

    @Bean("json-patch")
    public DirectComponent jsonPatch() {
        return new DirectComponent();
    }

    @Bean("json-mapping")
    public DirectComponent jsonMapping() {
        return new DirectComponent();
    }

    @Bean("xml-path")
    public DirectComponent xmlPath() {
        return new DirectComponent();
    }

    @Bean("dynamic-dmn")
    public DirectComponent dynamicDmn() {
        return new DirectComponent();
    }

    @Bean("dynamic-route")
    public DirectComponent dynamicRoute() {
        return new DirectComponent();
    }

    @Bean("dynamic-groovy")
    public DirectComponent dynamicGroovy() {
        return new DirectComponent();
    }
}
