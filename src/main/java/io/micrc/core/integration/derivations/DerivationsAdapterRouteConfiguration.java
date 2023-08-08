package io.micrc.core.integration.derivations;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.Result;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 衍生适配器路由定义和参数bean定义
 *
 * @author hyosunghan
 * @date 2022-09-21 14:00
 * @since 0.0.1
 */
public class DerivationsAdapterRouteConfiguration extends MicrcRouteBuilder {

    public static final String ROUTE_TMPL_DERIVATIONS_ADAPTER =
            DerivationsAdapterRouteConfiguration.class.getName() + ".derivationsAdapter";

    @Override
    public void configureRoute() throws Exception {

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://system");

        routeTemplate(ROUTE_TMPL_DERIVATIONS_ADAPTER)
                .templateParameter("name", null, "the derivations adapter name")
                .templateParameter("serviceName", null, "the derivations service name")
                .templateParameter("requestMapping", null, "the request mapping context")
                .templateParameter("responseMapping", null, "the response mapping context")
                .from("operate:{{name}}")
                .routeId("operate-{{name}}")
                .setProperty("serviceName", constant("{{serviceName}}"))
                .setProperty("requestMapping", constant("{{requestMapping}}"))
                .setProperty("responseMapping", constant("{{responseMapping}}"))
                // 1.请求映射
                .to("direct://request-mapping-derivations")
                // 2.执行逻辑
                .to("direct://execute-derivations")
                // 3.响应映射
                .to("direct://response-mapping-derivations")
                // 4.统一返回
                .to("direct://derivationsAdapterResult")
                .end();

        from("direct://execute-derivations")
                .toD("bean://${exchange.properties.get(serviceName)}?method=execute");

        from("direct://request-mapping-derivations")
                .setHeader("mappingContent", exchangeProperty("requestMapping"))
                .to("json-mapping://content");

        from("direct://response-mapping-derivations")
                .setHeader("mappingContent", exchangeProperty("responseMapping"))
                .to("json-mapping://content");

        from("direct://derivationsAdapterResult")
                .process(exchange -> {
                    String body = (String) exchange.getIn().getBody();
                    Object code = JsonUtil.readPath(body, "/code");
                    exchange.setProperty("code", code);
                    Object message = JsonUtil.readPath(body, "/message");
                    exchange.setProperty("message", message);
                })
                .unmarshal().json(Object.class)
                .choice()
                    .when(simple("${exchange.properties.get(code)} == null || ${exchange.properties.get(message)} == null"))
                        .bean(Result.class, "result(${in.body})")
                    .endChoice()
                .end();
    }

    /**
     * 应用衍生服务适配器路由参数Bean
     *
     * @author hyosunghan
     * @date 2022-09-21 14:00
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationDerivationsAdapterDefinition extends AbstractRouteTemplateParamDefinition {

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

        /**
         * 请求映射
         */
        String requestMapping;

        /**
         * 响应映射
         */
        String responseMapping;
    }
}