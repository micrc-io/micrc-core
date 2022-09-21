package io.micrc.core.integration.derivations.springboot;

import io.micrc.core.integration.derivations.DerivationsAdapterRouteConfiguration;
import io.micrc.core.integration.derivations.DerivationsAdapterRouteTemplateParameterSource;
import org.apache.camel.CamelContext;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 衍生适配器支持springboot自动配置
 *
 * @author hyosunghan
 * @date 2022-9-21 11:02
 * @since 0.0.1
 */
@Configuration
@Import({DerivationsAdapterRouteConfiguration.class})
public class DerivationsAdapterAutoConfiguration {

    /**
     * camel context config
     * before start中注入业务服务路由模版参数源
     * NOTE: CamelContextConfiguration可以存在多个，每个都会执行。也就是说，其他路由模版参数源也可以重新定义和注入
     *
     * @param source 衍生适配器路由模版参数源
     * @return
     */
    @Bean
    public CamelContextConfiguration derivationsAdapterContextConfiguration(
            DerivationsAdapterRouteTemplateParameterSource source) {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.getRegistry().bind("ApplicationDerivationsAdapterRouteTemplateParameterSource",
                        RouteTemplateParameterSource.class, source);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // leave it out
            }
        };
    }

    @Bean("operate")
    public DirectComponent derivationsAdapter() {
        DirectComponent derivationsAdapter = new DirectComponent();
        return derivationsAdapter;
    }
}
