package io.micrc.core.application.businesses.springboot;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteTemplateParameterSource;

/**
 * 业务服务支持springboot自动配置
 *
 * @author weiguan
 * @version 0.0.1
 * @date 2022-08-23 21:02
 */
@Configuration
public class BusinessesServiceAutoConfiguration {

    @Bean
    public CamelContextConfiguration contextConfiguration(
            ApplicationBusinessesServiceRouteTemplateParameterSource source) {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.getRegistry().bind("ApplicationBusinessesServiceRouteTemplateParameterSource",
                        RouteTemplateParameterSource.class, source);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // leave it out
            }
        };
    }
}
