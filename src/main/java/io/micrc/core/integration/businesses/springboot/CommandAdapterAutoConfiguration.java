package io.micrc.core.integration.businesses.springboot;

import io.micrc.core.integration.businesses.ApplicationCommandAdapterRouteConfiguration;
import io.micrc.core.integration.businesses.ApplicationCommandAdapterRouteTemplateParameterSource;
import org.apache.camel.CamelContext;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 业务服务适配器支持springboot自动配置
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Configuration
@Import({ApplicationCommandAdapterRouteConfiguration.class})
public class CommandAdapterAutoConfiguration {

    /**
     * camel context config
     * before start中注入业务服务路由模版参数源
     * NOTE: CamelContextConfiguration可以存在多个，每个都会执行。也就是说，其他路由模版参数源也可以重新定义和注入
     *
     * @param source 业务服务路由模版参数源
     * @return  CamelContextConfiguration
     */
    @Bean
    public CamelContextConfiguration applicationBusinessesAdapterContextConfiguration(
            ApplicationCommandAdapterRouteTemplateParameterSource source) {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.getRegistry().bind("ApplicationBusinessesAdapterRouteTemplateParameterSource",
                        RouteTemplateParameterSource.class, source);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // leave it out
            }
        };
    }

    @Bean("command")
    public DirectComponent businessesAdapter() {
        DirectComponent commandAdapter = new DirectComponent();
        return commandAdapter;
    }
}
