package io.micrc.core.integration.presentations;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.Result;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 展示适配器路由定义和参数bean定义
 *
 * @author hyosunghan
 * @date 2022-9-5 14:00
 * @since 0.0.1
 */
public class PresentationsAdapterRouteConfiguration extends MicrcRouteBuilder {

    public static final String ROUTE_TMPL_PRESENTATIONS_ADAPTER =
            PresentationsAdapterRouteConfiguration.class.getName() + ".presentationsAdapter";

    @Override
    public void configureRoute() throws Exception {

        routeTemplate(ROUTE_TMPL_PRESENTATIONS_ADAPTER)
                .templateParameter("name", null, "the presentations adapter name")
                .templateParameter("serviceName", null, "the presentations service name")
                .from("query:{{name}}")
                .to("presentations:{{serviceName}}")
                .unmarshal().json(Object.class)
                .bean(Result.class, "result(${in.body})")
                .end();
    }

    /**
     * 应用展示服务适配器路由参数Bean
     *
     * @author hyosunghan
     * @date 2022-9-5 14:00
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationPresentationsAdapterDefinition extends AbstractRouteTemplateParamDefinition {

        /**
         * 适配器名称
         */
        String name;

        /**
         * 应用服务名称 - 类简写名(SimpleName)
         *
         * @return
         */
        String serviceName;
    }
}