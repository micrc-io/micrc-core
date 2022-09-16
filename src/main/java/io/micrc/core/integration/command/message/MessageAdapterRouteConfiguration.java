package io.micrc.core.integration.command.message;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 * 消息接收适配器路由定义和参数bean定义
 *
 * @author tengwang
 * @date 2022-09-05 14:00
 * @since 0.0.1
 */
public class MessageAdapterRouteConfiguration extends RouteBuilder {

    public static final String ROUTE_TMPL_MESSAGE =
            MessageAdapterRouteConfiguration.class.getName() + ".messageAdapter";

    @Override
    public void configure() throws Exception {

        routeTemplate(ROUTE_TMPL_MESSAGE)
                .templateParameter("name", null, "the business adapter name")
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("commandPath", null, "the command full path")
                .from("message-adapter://{{name}}")
                .setHeader("CamelJacksonUnmarshalType").simple("{{commandPath}}")
                .to("dataformat:jackson:unmarshal?allow-unmarshall-type=true")
                .to("businesses://{{serviceName}}")
                .end();
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