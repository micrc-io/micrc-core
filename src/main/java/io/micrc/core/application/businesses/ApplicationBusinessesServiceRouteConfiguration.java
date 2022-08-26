package io.micrc.core.application.businesses;

import org.apache.camel.builder.RouteBuilder;

import io.micrc.core.application.AbstractApplicationServiceDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 应用业务服务路由定义和参数bean定义
 * 业务服务具体执行逻辑由模版定义，外部注解通过属性声明逻辑变量，由这些参数和路由模版构造执行路由
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-27 21:02
 */
public class ApplicationBusinessesServiceRouteConfiguration extends RouteBuilder {
    // 路由模版ID
    public static final String ROUTE_TMPL_BUSINESSES_SERVICE =
            ApplicationBusinessesServiceRouteConfiguration.class.getName() + ".businessesService";

    @Override
    public void configure() throws Exception {
        // todo 完成application businesses service 的路由模版
        // todo 1. 衍生服务集成，填充command（camel-rest-openapi）
        // todo 2. 执行dmn logic service，一共三个，前置检查、逻辑、后置检查，使用结果填充command（camel-rest）
        // todo 3. 存储事件（camel-bean）- 这里的bean使用spring data jpa的bean
        // todo 3.1 定义一个通用的message store entity及其spring data jpa的repository，
        //          注意datasource，entity manager和transaction manager不在这里定义和管理，
        //          而是在integration/persistence中，这里配置的时候会依赖这些定义
    }

    /**
     * 应用业务服务路由参数Bean
     *
     * @author weiguan
     * @since 0.0.1
     * @date 2022-08-27 23:02
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationBusinessesServiceDefinition extends AbstractApplicationServiceDefinition {
        // todo 定义所有路由模版所需的参数
    }
}
