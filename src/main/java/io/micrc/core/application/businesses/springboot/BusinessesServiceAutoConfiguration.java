package io.micrc.core.application.businesses.springboot;

import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteTemplateParameterSource;
import io.micrc.core.application.businesses.BusinessesServiceRouterExecution;
import org.apache.camel.CamelContext;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * 业务服务支持springboot自动配置
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Configuration
@Import({ApplicationBusinessesServiceRouteConfiguration.class, BusinessesServiceRouterExecution.class})
public class BusinessesServiceAutoConfiguration {

    /**
     * camel context config
     * before start中注入业务服务路由模版参数源
     * NOTE: CamelContextConfiguration可以存在多个，每个都会执行。也就是说，其他路由模版参数源也可以重新定义和注入
     *
     * @param source 业务服务路由模版参数源
     * @return  CamelContextConfiguration
     */
    @Bean
    public CamelContextConfiguration applicationBusinessesServiceContextConfiguration(
            ApplicationBusinessesServiceRouteTemplateParameterSource source) {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.getRegistry().bind("ApplicationBusinessesServiceRouteTemplateParameterSource",
                        RouteTemplateParameterSource.class, source);
                // 新事务策略，批量循环处理场景下使用，确保事务独立
                Registry registry = camelContext.getRegistry();
                JpaTransactionManager jpaTransactionManager = registry.findSingleByType(JpaTransactionManager.class);
                String propagationRequiresNew = "PROPAGATION_REQUIRES_NEW";
                SpringTransactionPolicy propagationRequiresNewPolicy = new SpringTransactionPolicy();
                propagationRequiresNewPolicy.setTransactionManager(jpaTransactionManager);
                propagationRequiresNewPolicy.setPropagationBehaviorName(propagationRequiresNew);
                registry.bind(propagationRequiresNew, propagationRequiresNewPolicy);
                // 该策略为事务默认策略，todo, 添加了上面新事务策略后，该策略需显式声明，否则会影响业务逻辑事务传播方式
                String propagationRequired = "PROPAGATION_REQUIRED";
                SpringTransactionPolicy propagationRequiredPolicy = new SpringTransactionPolicy();
                propagationRequiredPolicy.setTransactionManager(jpaTransactionManager);
                propagationRequiredPolicy.setPropagationBehaviorName(propagationRequired);
                registry.bind(propagationRequired, propagationRequiredPolicy);
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

    @Bean("businesses")
    public DirectComponent businesses() {
        DirectComponent businesses = new DirectComponent();
        return businesses;
    }


    @Bean("logic")
    public DirectComponent logic() {
        DirectComponent logic = new DirectComponent();
        return logic;
    }

    @Bean("executor-data-one")
    public DirectComponent executorDataOne() {
        return new DirectComponent();
    }

    @Bean("executor-data")
    public DirectComponent executorData() {
        return new DirectComponent();
    }
}
