package io.micrc.core.message;

import io.micrc.core.message.rabbit.store.RabbitIdempotentMessageRepository;
import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.message.store.IdempotentMessage;
import io.micrc.core.message.store.IdempotentMessageRepository;
import io.micrc.core.message.tracking.MessageTracker;
import io.micrc.core.message.tracking.MessageTrackerRepository;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Body;
import org.apache.camel.Consume;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息存储、发送路由，订阅路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 05:35
 * @since 0.0.1
 */
public class MessageRouteConfiguration extends RouteBuilder {

    @Autowired
    private KafkaTemplate<String, String> template;

    @Consume("eventstore://get-event-message")
    public EventMessage getEventMessage(@Body String body, @Header("currentCommandJson") String currentCommandJson) {
        EventMessage eventMessage = new EventMessage();
        eventMessage.setContent(currentCommandJson);
        eventMessage.setRegion(body);
        return eventMessage;
    }

    @Consume("eventstore://create-tracker")
    public MessageTracker create(MessageRouteConfiguration.EventsInfo.Event event) {
        MessageTracker tracker = new MessageTracker();
        tracker.setTopicName(event.getTopicName());
        tracker.setEventName(event.getEventName());
        tracker.setSenderName(event.getSenderAddress());
        tracker.setSequence(0L);
        tracker.setTrackerId(event.getSenderAddress() + "-" + event.getTopicName());
        return tracker;
    }

    @Consume("eventstore://tracker-move")
    public MessageTracker move(@Body MessageTracker tracker, @Header("eventMessages") List<EventMessage> eventMessages) {
        tracker.setSequence(eventMessages.get(eventMessages.size() - 1).getMessageId());
        return tracker;
    }

    @Consume("publish://sending-message")
    public void send(
            @Body EventMessage eventMessage,
            @Header("mappings") List<EventsInfo.EventMapping> mappings,
            @Header("tracker") MessageTracker tracker,
            @Header("type") String type
    ) {
        Map<String, EventsInfo.EventMapping> mappingMap = mappings.stream().collect(Collectors.toMap(EventsInfo.EventMapping::getReceiverAddress, i -> i, (i1, i2) -> i1));
        Message<?> objectMessage = MessageBuilder
                .withPayload(eventMessage.getContent())
                .setHeader(KafkaHeaders.TOPIC, tracker.getTopicName())
                .setHeader("sequence", eventMessage.getMessageId())
                .setHeader("sender", tracker.getSenderName())
                .setHeader("event", tracker.getEventName())
                .setHeader("mappingMap", mappingMap).build();
        ListenableFuture<SendResult<String, String>> future = template.send(objectMessage);
        future.completable().whenCompleteAsync((n, e) -> {
//            if (null != e) {
                ProducerRecord<String, String> producerRecord = n.getProducerRecord();
            Iterator<org.apache.kafka.common.header.Header> iterator = producerRecord.headers().iterator();
            StringBuilder headerString = new StringBuilder();
            while (iterator.hasNext()) {
                org.apache.kafka.common.header.Header header = iterator.next();
                String k = header.key();
                String v = new String(header.value());
                headerString.append(",").append(k).append("=").append(v);
            }
            System.out.println("producer header: " + headerString.substring(1));
            System.out.println("producer value: " + producerRecord.value());
            System.out.println("producer topic: " + n.getRecordMetadata().topic());
//            }
        });

    }

    @Consume("subscribe://idempotent-message")
    public IdempotentMessage idempotent(Map<String, Object> messageDetail) {
        IdempotentMessage idempotent = new IdempotentMessage();
        idempotent.setSender((String) messageDetail.get("sender"));
        idempotent.setSequence(Long.valueOf(messageDetail.get("sequence").toString()));
        idempotent.setReceiver((String) messageDetail.get("servicePath"));
        return idempotent;
    }

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
                .to("eventstore://get-event-message")
                .bean(EventMessageRepository.class, "save")
                .endChoice()
                .end()
                .end();
        // 调度发送主路由
        from("eventstore://sender")
                .routeId("eventstore://sender")
                .transacted()
                .bean(EventsInfo.class, "getAllEvents")
                .split(new SplitList()).parallelProcessing()
                    .setProperty("eventInfo", body())
                    .bean(MessageTrackerRepository.class, "findFirstBySenderNameAndTopicName(${body.getSenderAddress()},${body.getTopicName()})")
                    .choice()
                        .when(body().isNull())
                            .setBody(exchangeProperty("eventInfo"))
                            .log("创建跟踪器" + body())
                            .to("eventstore://create-tracker")
                        .endChoice()
                    .end()
                    .setProperty("currentTracker", body())
                    .bean(MessageTrackerRepository.class, "save")

    //                .bean(RabbitErrorMessageRepository.class, "findErrorMessageByExchangeAndChannelLimitByCount(${exchange.properties.get(currentTracker).getExchange()}, ${exchange.properties.get(currentTracker).getChannel()}, 100)")
    //                .setHeader("errorMessageCount", simple("${body.size}"))
    //                .setProperty("errorEvents", body())
                    .process(exchange -> {
    //                    Integer errorMessageCount = (Integer) exchange.getIn().getHeader("errorMessageCount");
    //                    exchange.getIn().setHeader("normalMessageCount", 100 - errorMessageCount);
                        exchange.getIn().setHeader("normalMessageCount", 1000);
                    })
                    .bean(EventMessageRepository.class, "findEventMessageByRegionAndCurrentSequenceLimitByCount(" +
                            "${exchange.properties.get(currentTracker).getEventName()}," +
                            "${exchange.properties.get(currentTracker).getSequence()}, ${header.normalMessageCount})")
                    .setProperty("normalEvents", body())
                    .choice()
                        .when(simple("${body.size} > 0"))
                            .setBody(exchangeProperty("currentTracker"))
                            .setHeader("eventMessages", simple("${exchange.properties.get(normalEvents)}"))
                            .to("eventstore://tracker-move")
                            .bean(MessageTrackerRepository.class, "save")
                        .endChoice()
                    .end()
    //                .setBody(exchangeProperty("errorEvents"))
    //                .split(new RabbitMessageRouteConfiguration.SplitList()).parallelProcessing()
    //                .to("publish://send-error")
    //                .end()
                    .setBody(exchangeProperty("normalEvents"))
                    .split(new SplitList()).parallelProcessing()
                        .to("publish://send-normal")
                        .end()
                    .end()
                .end();

        // 通用异常消息发送路由
        from("publish://send-error")
                .routeId("direct://send-error")
                .log("1111")
                .end();

        // 通用正常消息发送路由
        from("publish://send-normal")
                .routeId("direct://send-normal")
                .setHeader("normalMessage", body())
                .setHeader("mappings", simple("${exchange.properties.get(eventInfo).getEventMappings()}"))
//                .setBody(simple("${body.getContent()}"))
//                .to("json-mapping://file")
//                .setHeader("sendContext", body())
//                .setBody(header("normalMessage"))
//                .setHeader("content", header("sendContext"))
//                .to("eventstore://message-set-content")
//                .setHeader("currentMessage", body())
//                .to("publish://get-template")
//                .setHeader("template", body())
                .setHeader("type", constant("NORMAL"))
                .setHeader("tracker", exchangeProperty("currentTracker"))
//                .setBody(header("currentMessage"))
                .to("publish://sending-message")
                .end();

        // 幂等性消费仓库检查
        from("subscribe://idempotent-check")
                .routeId("subscribe://idempotent-check")
                .setHeader("messageDetail", body())
                .bean(IdempotentMessageRepository.class, "findFirstBySequenceAndReceiver(${body.get(sequence)}, ${body.get(servicePath)})")
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

        // 死信监听路由
        from("subscribe://dead-message")
                .routeId("subscribe://dead-message")
                .log("4444")
                .end();

        // 发送失败监听路由
        from("publish://error-sending-resolve")
                .routeId("publish://error-sending-resolve")
                .log("5555")
                .end();

        // 发送成功监听路由,如发送的是异常消息需要移除异常表数据
        from("publish://success-sending-resolve")
                .routeId("publish://success-sending-resolve")
                .log("6666")
                .end();
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

    @NoArgsConstructor
    public static class EventsInfo {

        private final List<Event> eventsCache = new ArrayList<>();
        private final HashMap<String, Event> eventsInfo = new HashMap<>();

        public void put(String key, Event value) {
            this.eventsInfo.put(key, value);
            this.eventsCache.add(value);
        }

        public List<Event> getAllEvents() {
            return this.eventsCache;
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
             * 发送地址名
             */
            private String senderAddress;

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

            /**
             * 接收方地址 - 放入消息header
             *
             * @return
             */
            private String receiverAddress;
        }
    }

}



