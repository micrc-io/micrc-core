package io.micrc.core.message;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.message.store.EventMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;

/**
 * 消息MOCK发送路由定义和参数bean定义
 * 消息MOCK发送具体执行逻辑由模版定义，外部注解通过属性声明逻辑变量，由这些参数和路由模版构造执行路由
 *
 * @author hyosunghan
 * @date 2022-09-19 11:23
 * @since 0.0.1
 */
public class MessageMockSenderRouteConfiguration extends MicrcRouteBuilder {

    // 路由模版ID
    public static final String ROUTE_TMPL_MESSAGE_SENDER =
            MessageMockSenderRouteConfiguration.class.getName() + ".messageSender";

    /**
     * 配置消息MOCK发送路由模板
     *
     * @throws Exception
     */
    @Override
    public void configureRoute() throws Exception {

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://system");

        routeTemplate(ROUTE_TMPL_MESSAGE_SENDER)
                // 设置模板参数
                .templateParameter("listenerName", null, "the message listener name")
                .templateParameter("eventName", null, "the message event name")
                .templateParameter("topicName", null, "the message topic name")
                .templateParameter("serviceName", null, "the service name")
                // 指定service名称为入口端点
                .from("rest:post:{{listenerName}}")
                .setProperty("eventName", simple("{{eventName}}"))
                .setProperty("topicName", simple("{{topicName}}"))
                .setProperty("serviceName", simple("{{serviceName}}"))
                .convertBodyTo(String.class)
                .process(exchange -> {
                    // 设置消息体
                    String command = exchange.getIn().getBody(String.class);
                    EventMessage eventMessage = new EventMessage();
                    eventMessage.setContent(command);
                    eventMessage.setRegion(exchange.getProperty("eventName", String.class));
                    exchange.getIn().setBody(eventMessage);
                    // 交换区模拟事件信息
                    MessageRouteConfiguration.EventsInfo.EventMapping eventMapping = MessageRouteConfiguration.EventsInfo.EventMapping
                            .builder()
                            .mappingPath(".")
                            .mappingKey(exchange.getProperty("serviceName", String.class))
                            .receiverAddress("MOCK RECEIVER")
                            .build();
                    MessageRouteConfiguration.EventsInfo.Event event = MessageRouteConfiguration.EventsInfo.Event
                            .builder()
                            .topicName(exchange.getProperty("topicName", String.class))
                            .eventName(exchange.getProperty("eventName", String.class))
                            .senderAddress("MOCK SENDER")
                            .eventMappings(Arrays.asList(eventMapping))
                            .build();
                    exchange.setProperty("eventInfo", event);
                })
                .to("publish://send-normal")
                .end();
    }

    /**
     * 消息MOCK发送路由参数Bean
     *
     * @author hyosunghan
     * @date 2022-09-19 11:30
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class MessageMockSenderDefinition extends AbstractRouteTemplateParamDefinition {

        /**
         * 监听器名称
         */
        protected String listenerName;

        /**
         * 事件名称
         */
        protected String eventName;

        /**
         * 主题名称
         */
        protected String topicName;

        /**
         * 服务名称
         */
        protected String serviceName;
    }
}