package io.micrc.core.application.businesses.springboot;

import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteTemplateParameterSource;
import org.apache.camel.CamelContext;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.rest.RestComponent;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 业务服务支持springboot自动配置
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-23 21:02
 */
@Configuration
@Import({ApplicationBusinessesServiceRouteConfiguration.class})
public class BusinessesServiceAutoConfiguration {

    /**
     * camel context config
     * before start中注入业务服务路由模版参数源
     * NOTE: CamelContextConfiguration可以存在多个，每个都会执行。也就是说，其他路由模版参数源也可以重新定义和注入
     *
     * @param source 业务服务路由模版参数源
     * @return
     */
    @Bean
    public CamelContextConfiguration applicationBusinessesServiceContextConfiguration(
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

    @Bean("repository")
    public BeanComponent repository() {
        BeanComponent repository = new BeanComponent();
        return repository;
    }

    @Bean("logic")
    public RestComponent logic() {
        RestComponent logic = new RestComponent();
        return logic;
    }

    @Bean("businesses")
    public DirectComponent businesses() {
        DirectComponent businesses = new DirectComponent();
        return businesses;
    }
}
