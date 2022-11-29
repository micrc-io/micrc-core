package io.micrc.core.message;

import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 消息存储、发送路由，订阅路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 05:35
 * @since 0.0.1
 */
public class MessageRouteConfiguration extends RouteBuilder {


    @Autowired
    private RabbitTemplate template;

    @Override
    public void configure() throws Exception {
        // 通用消息存储路由
        from("eventstore://store")
                .routeId("eventstore://store")
                .setHeader("currentCommandJson", body())
                .setHeader("pointer", constant("/event/eventName"))
                .to("json-patch://select")
                .choice()
                .when(body().isNotNull())
                .bean(EventMessage.class, "store(${header.currentCommandJson}, ${in.body})")
                .bean(EventMessageRepository.class, "save")
                .endChoice()
                .end()
                .end();
        // 调度发送主路由
        from("eventstore://sender")
                .routeId("eventstore://sender")
                .end();

        // 通用异常消息发送路由
        from("publish://send-error")
                .routeId("direct://send-error")
                .end();

        // 通用正常消息发送路由
        from("publish://send-normal")
                .routeId("direct://send-normal")
                .end();

        // 幂等性消费仓库检查
        from("subscribe://idempotent-check")
                .routeId("subscribe://idempotent-check")
                .end();

        // 死信监听路由
        from("subscribe://dead-message")
                .routeId("subscribe://dead-message")
                .end();

        // 发送失败监听路由
        from("publish://error-sending-resolve")
                .routeId("publish://error-sending-resolve")
                .end();

        // 发送成功监听路由,如发送的是异常消息需要移除异常表数据
        from("publish://success-sending-resolve")
                .routeId("publish://success-sending-resolve")
                .end();
    }

    @NoArgsConstructor
    public static class EventsInfo {

        private final List<Event> eventsCache = new ArrayList<>();
        private final HashMap<String, Event> eventsInfo = new HashMap<>();

        public void put(String key, Event value) {
            this.eventsInfo.put(key, value);
            this.eventsCache.add(value);
        }

        public Event get(String key) {
            return eventsInfo.get(key);
        }

        @Data
        @SuperBuilder
        public static class Event {

            /**
             * 发送主题名称
             */
            private String topicName;

            /**
             * 事件名称
             */
            private String eventName;

            /**
             * 消息映射
             */
            private List<EventMapping> eventMappings;

        }

        @Data
        @SuperBuilder
        public static class EventMapping {

            /**
             * 映射转换Key - 以对端用例英文名做key
             *
             * @return
             */
            private String mappingKey;

            /**
             * 对端映射文件地址
             *
             * @return
             */
            private String mappingPath;
        }
    }

}



