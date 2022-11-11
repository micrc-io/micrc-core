package io.micrc.core.integration.message;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.ErrorInfo;
import io.micrc.core.rpc.Result;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 消息接收适配器路由定义和参数bean定义
 *
 * @author tengwang
 * @date 2022-09-05 14:00
 * @since 0.0.1
 */
public class MessageAdapterRouteConfiguration extends MicrcRouteBuilder {

    public static final String ROUTE_TMPL_MESSAGE =
            MessageAdapterRouteConfiguration.class.getName() + ".messageAdapter";

    @Override
    public void configureRoute() throws Exception {

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://system");

        routeTemplate(ROUTE_TMPL_MESSAGE)
                .templateParameter("name", null, "the business adapter name")
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("commandPath", null, "the command full path")
                .from("message://{{name}}")
                .routeId("message://{{name}}")
                .setHeader("CamelJacksonUnmarshalType").simple("{{commandPath}}")
                .to("dataformat:jackson:unmarshal?allow-unmarshall-type=true")
                .toD("bean://{{serviceName}}?method=execute")
                .to("direct://messageAdapterResult")
                .end();

        // 命令适配器结果处理
        from("direct://messageAdapterResult")
                .unmarshal().json(Object.class)
                .setProperty("command", body())
                .marshal().json().convertBodyTo(String.class)
                .setHeader("pointer", simple("/error"))
                .to("json-patch://select")
                .marshal().json().convertBodyTo(String.class)
                .unmarshal().json(ErrorInfo.class)
                // TODO 设置command对Data的映射,使用protocol读取其x-result-mapping.暂时使用command替代
                .bean(Result.class, "result(${body}, ${exchange.properties.get(command)})");
    }

    /**
     * 应用业务服务适配器路由参数Bean
     *
     * @author tengwang
     * @date 2022-09-05 14:00
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationMessageRouteTemplateParamDefinition extends AbstractRouteTemplateParamDefinition {

        /**
         * 适配器名称
         */
        String name;

        /**
         * 应用服务全路径
         */
        String commandPath;

        /**
         * 应用服务名称 - 类简写名(SimpleName)
         *
         * @return
         */
        String serviceName;

        /**
         * 接收实体名称
         *
         * @return
         */
        String receiveEntityName;

        /**
         * 事件名称
         *
         * @return
         */
        String event;

        /**
         * 是否顺序消费
         *
         * @return
         */
        Boolean ordered;
    }
}