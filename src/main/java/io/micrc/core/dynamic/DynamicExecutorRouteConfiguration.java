package io.micrc.core.dynamic;

import io.micrc.core.MicrcRouteBuilder;

/**
 * 应用业务服务适配器路由定义和参数bean定义
 *
 * @author tengwang
 * @date 2022-09-05 14:00
 * @since 0.0.1
 */
public class DynamicExecutorRouteConfiguration extends MicrcRouteBuilder {

    @Override
    public void configureRoute() throws Exception {
        onException(Exception.class)
                .handled(false)
                .end();
        /**
        header: executeType 执行类型
                script 执行脚本
                from 动态端点-仅针对ROUTE类型
                decision 决策结果节点-仅针对DMN-默认为result
         */
        from("dynamic-executor://execute")
                .routeId("dynamic-executor-execute")
                .choice()
                    .when(header("executeType").isEqualTo("DMN"))
                        .to("dynamic-dmn://execute")
                    .when(header("executeType").isEqualTo("ROUTE"))
                        .setHeader("route", header("script"))
                        .to("dynamic-route://execute")
                    .when(header("executeType").isEqualTo("JSLT"))
                        .setHeader("mappingContent", header("script"))
                        .to("json-mapping://content")
                    .endChoice()
                .removeHeader("executeType")
                .removeHeader("script")
                .end();
    }
}

