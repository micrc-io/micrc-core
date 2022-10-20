package io.micrc.core.integration.camel.springboot;

import io.micrc.core.integration.camel.CamelRouteAdapterRouteConfiguration;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({CamelRouteAdapterRouteConfiguration.class})
public class CamelRouteAdapterAutoConfiguration {

    @Bean("camel-route")
    public DirectComponent businessesAdapter() {
        DirectComponent camelRouteAdapter = new DirectComponent();
        return camelRouteAdapter;
    }
}
