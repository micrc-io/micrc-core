package io.micrc.core.message;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 * 消息存储、发送路由，订阅路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 05:35
 * @since 0.0.1
 */
public class MessageRouteConfiguration extends RouteBuilder {

    public static final String ROUTE_TMPL_MESSAGE_PUBLISHER =
            MessageRouteConfiguration.class.getName() + ".messageSubscriber";

    @Override
    public void configure() throws Exception {
        from("event-store://store")
                .setHeader("pointer", constant("/event"))
                .to("json-patch://select")
                .bean(StoredEvent.class, "store(${exchange.properties.get(commandJson)}, ${in.body})")
                .setHeader("event", body())
                .setBody(simple("insert into message_message_store (message_id, create_time, content, region) values ('${in.header.event.messageId}', ${in.header.event.createTime}, '${in.header.event.content}', '${in.header.event.region}')"))
                .to("jdbc:datasource?useHeadersAsParameters=true")
                .end();
        // TODO 2. 调度转发消息通用路由，从事件表中获取消息，使用消息跟踪表控制发送，使用publish协议（spring-rabbitmq组件）发送
        // TODO 通用消息发送器路由
        from("event-store://sender")
                .log("TODO 消息通用发送器路由")
                .end();

        // TODO 通用消息发送路由
        from("direct://send") // publish
                .log("TODO 消息通用发送器路由")
                .end();
        // TODO 事件订阅路由模版，使用subscribe协议（spring-rabbitmq组件）监听
        // TODO 鉴别消息的事件类型，执行幂等，调用integration中的消息监听适配协议（message）
        routeTemplate(ROUTE_TMPL_MESSAGE_PUBLISHER)
                .from("direct://aggrName-eventName-LogicName")// subscribe
                .log("TODO 事件订阅路由模版")
                .transacted()
                .log("1.顺序消费者")
                .log("2.幂等消费者")
                .log("3.发送至消息适配器")
                .end();
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class MessageDefinition extends AbstractRouteTemplateParamDefinition {

    }
}

@Data
class StoredEvent {

    private String messageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");

    private Long createTime = System.currentTimeMillis();

    private String content;

    private Long sequence;

    private String region;

    public StoredEvent store(String command, String event) {
        StoredEvent storedEvent = new StoredEvent();
        storedEvent.setContent(command);
        storedEvent.setRegion(event);
        return storedEvent;
    }
}
