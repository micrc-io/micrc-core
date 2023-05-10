package io.micrc.core.dynamic.springboot;

import io.micrc.core.dynamic.DynamicExecutorRouteConfiguration;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({DynamicExecutorRouteConfiguration.class})
public class DynamicExecutorAutoConfiguration {

    @Bean("dynamic-executor")
    public DirectComponent businessesAdapter() {
        DirectComponent camelRouteAdapter = new DirectComponent();
        return camelRouteAdapter;
    }
}
