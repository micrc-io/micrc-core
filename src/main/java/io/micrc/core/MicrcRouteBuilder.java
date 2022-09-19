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
        // 测试用
        interceptSendToEndpoint("businesses:CachedService").skipSendToOriginalEndpoint()
            .when(simple("${header.WrappedRouter} == null || ${header.WrappedRouter} == false"))
            .setHeader("WrappedRouterBean")
                .groovy("header.CamelInterceptedEndpoint.replace('businesses:', '')")
            .toD("bean:${header.WrappedRouterBean}?method=exec(${body})")
            // .bean("cachedRouterWrapper", "exec(${header.CamelInterceptedEndpoint}, ${body})")
            .log("intercept businesses");

        interceptSendToEndpoint("businesses:*")
            .log("拦截所有businesses服务, 使用bean调用使@CacheEvict生效");
        interceptSendToEndpoint("presentations-service:*")
            .log("拦截所有presentations服务, 使用bean调用使@Cacheable生效 - **韩晓星把协议名字改了**");
        interceptSendToEndpoint("derivatives:*")
            .log("拦截所有derivatives服务, 使用bean调用使@Cacheable生效 - **韩晓星注意命名**");
        interceptSendToEndpoint("command:*")
            .log("拦截所有command适配, 使用bean调用使命令适配器的@Cacheable生效 - **王腾调整integration中的包结构**");
        interceptSendToEndpoint("query:*")
            .log("拦截所有query适配, 使用bean调用使展示适配器的@Cacheable生效 - **韩晓星改协议名**");
        interceptSendToEndpoint("message:*")
            .log("拦截所有message适配, 使用bean调用使消息监听适配器的@Cacheable生效 - **王腾调整integration中的包结构**");
        interceptSendToEndpoint("rpc:*")
            .log("拦截所有rpc适配, 使用bean调用使衍生适配器的@Cacheable生效 - **韩晓星注意命名**");

        configureRoute();
    }

    public abstract void configureRoute() throws Exception;
}
