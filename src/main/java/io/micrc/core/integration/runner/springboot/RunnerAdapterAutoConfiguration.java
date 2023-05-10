package io.micrc.core.integration.runner.springboot;

import io.micrc.core.integration.businesses.ApplicationCommandAdapterRouteConfiguration;
import io.micrc.core.integration.businesses.ApplicationCommandAdapterRouteTemplateParameterSource;
import io.micrc.core.integration.runner.RunnerAdapterRouteConfiguration;
import io.micrc.core.integration.runner.RunnerAdapterRouteTemplateParameterSource;
import io.micrc.core.integration.runner.RunnerAdapterRouterExecution;
import org.apache.camel.CamelContext;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 程序执行适配器支持springboot自动配置
 *
 * @author xwyang
 * @date 2023-04-08 16:42
 * @since 0.0.1
 */
@Configuration
@Import({RunnerAdapterRouteConfiguration.class, RunnerAdapterRouterExecution.class})
public class RunnerAdapterAutoConfiguration {


    @Bean
    public CamelContextConfiguration runnerAdapterContextConfiguration(
            RunnerAdapterRouteTemplateParameterSource source) {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.getRegistry().bind("RunnerAdapterRouteTemplateParameterSource",
                        RouteTemplateParameterSource.class, source);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // leave it out
            }
        };
    }

    @Bean("runner")
    public DirectComponent runnerAdapter() {
        return new DirectComponent();
    }

    @Bean("executor-content-mapping")
    public DirectComponent contentMapping() {
        return new DirectComponent();
    }
}
