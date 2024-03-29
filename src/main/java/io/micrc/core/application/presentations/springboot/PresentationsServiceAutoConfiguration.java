package io.micrc.core.application.presentations.springboot;

import io.micrc.core.application.presentations.ApplicationPresentationsServiceRouteTemplateParameterSource;
import org.apache.camel.CamelContext;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 展示服务支持springboot自动配置
 *
 * @author hyosunghan
 * @date 2022-9-2 13:12
 * @since 0.0.1
 */
@Configuration
public class PresentationsServiceAutoConfiguration {

    /**
     * camel context config
     * before start中注入展示服务路由模版参数源
     * NOTE: CamelContextConfiguration可以存在多个，每个都会执行。也就是说，其他路由模版参数源也可以重新定义和注入
     *
     * @param source 展示服务路由模版参数源
     * @return  CamelContextConfiguration
     */
    @Bean("presentationsServiceCamelContextConfiguration")
    public CamelContextConfiguration contextConfiguration(
            ApplicationPresentationsServiceRouteTemplateParameterSource source) {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.getRegistry().bind("ApplicationPresentationsServiceRouteTemplateParameterSource",
                        RouteTemplateParameterSource.class, source);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // leave it out
            }
        };
    }

    @Bean("presentations")
    public DirectComponent presentationsService() {
        DirectComponent presentationsService = new DirectComponent();
        return presentationsService;
    }
}
