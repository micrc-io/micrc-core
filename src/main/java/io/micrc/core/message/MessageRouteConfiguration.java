package io.micrc.core.message;

import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.message.store.IdempotentMessageRepository;
import io.micrc.core.message.tracking.ErrorMessageRepository;
import io.micrc.core.message.tracking.MessageTrackerRepository;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Consume;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

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
                .setHeader("pointer", constant("/event"))
                .to("json-patch://select")
                .bean(EventMessage.class, "store(${exchange.properties.get(commandJson)}, ${in.body})")
                .bean(EventMessageRepository.class, "save")
                .end();
        // 调度发送主路由
        from("eventstore://sender")
                .routeId("eventstore://sender")
                .transacted()
                .bean(EventsInfo.class, "getAllEvents")
                .split(new SplitList()).parallelProcessing()
                    .setProperty("eventInfo", body())
                    .bean(MessageTrackerRepository.class, "findFirstByChannel(${body.getChannel()})")
                    .choice()
                        .when(body().isNull())
                            .setBody(exchangeProperty("eventInfo"))
                            .to("eventstore://create-tracker")
                        .endChoice()
                    .end()
                    .setProperty("currentTracker", body())
                    .bean(ErrorMessageRepository.class, "findErrorMessageByExchangeAndChannelLimitByCount(${exchange.properties.get(currentTracker).getExchange()}, ${exchange.properties.get(currentTracker).getChannel()}, 100)")
                    .setHeader("errorMessageCount", simple("${body.size}"))
                    .setProperty("errorEvents", body())
                    .process(exchange -> {
                        Integer errorMessageCount = (Integer) exchange.getIn().getHeader("errorMessageCount");
                        exchange.getIn().setHeader("normalMessageCount", 100 - errorMessageCount);
                    })
                    .bean(EventMessageRepository.class, "findEventMessageByRegionAndCurrentSequenceLimitByCount(${exchange.properties.get(currentTracker).getRegion()}, ${exchange.properties.get(currentTracker).getSequence()}, ${header.normalMessageCount})")
                    .setProperty("normalEvents", body())
                    .choice()
                        .when(simple("${body.size} > 0"))
                            .setBody(exchangeProperty("currentTracker"))
                            .setHeader("eventMessages", simple("${exchange.properties.get(normalEvents)}"))
                            .to("eventstore://tracker-move")
                            .bean(MessageTrackerRepository.class, "save")
                        .endChoice()
                    .end()
                    .setBody(exchangeProperty("errorEvents"))
                    .split(new SplitList()).parallelProcessing()
                        .to("publish://send-error")
                    .end()
                    .setBody(exchangeProperty("normalEvents"))
                    .split(new SplitList()).parallelProcessing()
                        .to("publish://send-normal")
                    .end()
                .end()
                .end();
        // 通用异常消息发送路由
        from("publish://send-error") // publish
                .routeId("direct://send-error")
                .setHeader("errorMessage", body())
                .setBody(simple("${body.sequence}"))
                .bean(EventMessageRepository.class, "findEventMessageBySequence")
                .setHeader("normalMessage", body())
                .setHeader("mappingFilePath", simple("${exchange.properties.get(eventInfo).getMappingPath()}"))
                .setBody(simple("${body.getContent()}"))
                .to("json-mapping://file")
                .setHeader("sendContext", body())
                .setBody(header("errorMessage"))
                .to("eventstore://error-message-sending")
                .bean(ErrorMessageRepository.class, "save")
                .setBody(header("normalMessage"))
                .setHeader("content", header("sendContext"))
                .to("eventstore://message-set-content")
                .setHeader("currentMessage", body())
                .to("publish://get-template")
                .setHeader("template", body())
                .setHeader("type", constant("ERROR"))
                .setHeader("tracker", exchangeProperty("currentTracker"))
                .setBody(header("currentMessage"))
                .to("publish://sending")
                .end();

        // 通用正常消息发送路由
        from("publish://send-normal")
                .routeId("direct://send-normal")
                .setHeader("normalMessage", body())
                .setHeader("mappingFilePath", simple("${exchange.properties.get(eventInfo).getMappingPath()}"))
                .setBody(simple("${body.getContent()}"))
                .to("json-mapping://file")
                .setHeader("sendContext", body())
                .setBody(header("normalMessage"))
                .setHeader("content", header("sendContext"))
                .to("eventstore://message-set-content")
                .setHeader("currentMessage", body())
                .to("publish://get-template")
                .setHeader("template", body())
                .setHeader("type", constant("NORMAL"))
                .setHeader("tracker", exchangeProperty("currentTracker"))
                .setBody(header("currentMessage"))
                .to("publish://sending")
                .end();

        // 幂等性消费仓库检查
        from("subscribe://idempotent-check")
                .routeId("subscribe://idempotent-check")
                .setHeader("messageDetail", body())
                .bean(IdempotentMessageRepository.class, "findFirstByExchangeAndChannelAndSequenceAndRegion(${body.get(exchange)}, ${body.get(channel)}, ${body.get(sequence)}, ${body.get(region)})")
                .choice()
                    .when(body().isNull())
                        .setBody(header("messageDetail"))
                        .to("subscribe://idempotent-message")
                        .bean(IdempotentMessageRepository.class, "save")
                        .setBody(constant(false))
                    .endChoice()
                    .otherwise()
                        .setBody(constant(true))
                    .endChoice()
                .end();
        // 死信监听路由,落盘本地Tracker关联表
        from("subscribe://dead-message")
                .routeId("subscribe://dead-message")
                .transacted()
                .to("eventstore://dead-message-store")
                .bean(ErrorMessageRepository.class, "save")
                .end();

        // 发送失败监听路由,落盘本地Tracker关联表
        from("publish://error-sending-resolve")
                .routeId("publish://error-sending-resolve")
                .transacted()
                .log("发送失败监听-落盘异常事件")
                // FIXME 存在交换区不存在时调度重试导致消息重复落盘问题,需修复
                .choice()
                    .when(header("type").isEqualTo("ERROR"))
                        .bean(ErrorMessageRepository.class, "findFirstByExchangeAndChannelAndSequenceAndRegion(${body.get(exchange)}, ${body.get(channel)}, ${body.get(sequence)}, ${body.get(region)})")
                        .to("eventstore://send-error-error-message-store")
                        .bean(ErrorMessageRepository.class, "save")
                    .endChoice()
                    .when(header("type").isEqualTo("NORMAL"))
                        .to("eventstore://send-normal-error-message-store")
                        .bean(ErrorMessageRepository.class, "save")
                    .endChoice()
                .end()
                .end();
        // 发送失败监听路由,落盘本地Tracker关联表
        from("publish://error-return-resolve")
                .routeId("publish://error-return-resolve")
                .transacted()
                .log("发送失败监听-落盘异常事件")
                // FIXME 存在交换区不存在时调度重试导致消息重复落盘问题,需修复
                .choice()
                .when(header("type").isEqualTo("ERROR"))
                .bean(ErrorMessageRepository.class, "findFirstByExchangeAndChannelAndSequenceAndRegion(${body.get(exchange)}, ${body.get(channel)}, ${body.get(sequence)}, ${body.get(region)})")
                .to("eventstore://send-error-return-message-store")
                .bean(ErrorMessageRepository.class, "save")
                .endChoice()
                .when(header("type").isEqualTo("NORMAL"))
                .to("eventstore://send-normal-return-message-store")
                .bean(ErrorMessageRepository.class, "save")
                .endChoice()
                .end()
                .end();
        // 发送成功监听路由,如发送的是异常消息需要移除异常表数据
        from("publish://success-sending-resolve")
                .routeId("publish://success-sending-resolve")
                .transacted()
                .choice()
                    .when(header("type").isEqualTo("ERROR"))
                        .bean(ErrorMessageRepository.class, "deleteByExchangeAndChannelAndSequenceAndRegion(${body.get(exchange)}, ${body.get(channel)}, ${body.get(sequence)}, ${body.get(region)})")
                    .end()
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

        public Map<String, Event> getMaps() {
            return this.eventsInfo;
        }

        public List<Event> getAllEvents() {
            return this.eventsCache;
        }

        public Iterator<Event> iterator() {
            return eventsCache.iterator();
        }

        @Data
        @SuperBuilder
        public static class Event {

            /**
             * 交换区名称
             */
            private String exchangeName;

            /**
             * 事件名称
             */
            private String eventName;

            /**
             * 发送通道
             */
            private String channel;

            /**
             * 对端映射文件地址
             */
            private String mappingPath;

            /**
             * 是否顺序消息
             */
            private Boolean ordered;
        }
    }

    @Consume("publish://get-template")
    public RabbitTemplate getTemplate() {
        return this.template;
    }
    /**
     * 对象列表拆分器 -- TODO 通用 -- 抽走
     */
    public class SplitList extends ExpressionAdapter {
        @Override
        public Object evaluate(Exchange exchange) {
            @SuppressWarnings("unchecked")
            List<Object> objects = (List<Object>) exchange.getIn().getBody();
            if (null != objects) {
                return objects.iterator();
            } else {
                return null;
            }
        }
    }

}



