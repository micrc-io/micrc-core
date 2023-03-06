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
                .from("operate:{{name}}")
                .routeId("operate-{{name}}")
                .toD("bean://{{serviceName}}?method=execute")
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
    }
}