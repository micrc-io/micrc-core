package io.micrc.core.integration.presentations;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.Result;
import io.micrc.lib.JsonUtil;
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

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://system");

        routeTemplate(ROUTE_TMPL_PRESENTATIONS_ADAPTER)
                .templateParameter("name", null, "the presentations adapter name")
                .templateParameter("serviceName", null, "the presentations service name")
                .templateParameter("requestMapping", null, "the request mapping context")
                .templateParameter("responseMapping", null, "the response mapping context")
                .from("query:{{name}}")
                .setProperty("requestMapping", simple("{{requestMapping}}"))
                .setProperty("responseMapping", simple("{{responseMapping}}"))
                // 1.请求映射
                .to("direct://request-mapping-presentations")
                // 2.执行逻辑
                .toD("bean://{{serviceName}}?method=execute")
                // 3.响应映射
                .to("direct://response-mapping-presentations")
                // 4.统一返回
                .to("direct://presentationsAdapterResult")
                .end();

        from("direct://request-mapping-presentations")
                .setHeader("mappingContent", exchangeProperty("requestMapping"))
                .to("json-mapping://content");

        from("direct://response-mapping-presentations")
                .setHeader("mappingContent", exchangeProperty("responseMapping"))
                .to("json-mapping://content");

        from("direct://presentationsAdapterResult")
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