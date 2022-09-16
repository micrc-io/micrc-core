package io.micrc.core;

import org.apache.camel.builder.RouteBuilder;

/**
 * micrc base route builder
 * support global interceptor, exception and error handler and so on.
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-16 14:57
 */
public abstract class MicrcRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        interceptSendToEndpoint("businesses*").skipSendToOriginalEndpoint()
            .when(simple("${header.CacheWrapper} == null"))
            .bean("cachedRouterWrapper", "exec(${header.CamelInterceptedEndpoint}, ${body})")
            .log("intercept businesses");
        configureRoute();
    }

    public abstract void configureRoute() throws Exception;
}
