package io.micrc.core.integration.camel;

import io.micrc.core.MicrcRouteBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.support.ResourceHelper;

/**
 * 应用业务服务适配器路由定义和参数bean定义
 *
 * @author tengwang
 * @date 2022-09-05 14:00
 * @since 0.0.1
 */
public class CamelRouteAdapterRouteConfiguration extends MicrcRouteBuilder {

    @Override
    public void configureRoute() throws Exception {
        onException(Exception.class)
                .handled(false)
                .end();
        // TODO 要替换脚本里的routeId
        from("camel-route://execute")
                .routeId("camel-route")
                .process(exchange -> {
                    CamelContext context = exchange.getContext();
                    Route camelRoute = context.getRoute((String) exchange.getIn().getHeader("from"));
                    if (null == camelRoute) {
                        ExtendedCamelContext ec = context.adapt(ExtendedCamelContext.class);
                        ec.getRoutesLoader().loadRoutes(ResourceHelper.fromString(exchange.getIn().getHeader("from") + ".xml", (String) exchange.getIn().getHeader("route")));
                    }
                })
                .toD("${header.from}")
                .removeHeader("from")
                .removeHeader("route")
                .end();
    }
}

