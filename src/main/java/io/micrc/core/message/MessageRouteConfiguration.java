package io.micrc.core.message;

import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.message.store.IdempotentMessage;
import io.micrc.core.message.store.IdempotentMessageRepository;
import io.micrc.core.message.error.ErrorMessage;
import io.micrc.core.message.error.ErrorMessageRepository;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Body;
import org.apache.camel.Consume;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 消息存储、发送路由，订阅路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 05:35
 * @since 0.0.1
 */
public class MessageRouteConfiguration extends RouteBuilder {

    public static Long startTime;

    public static AtomicInteger count = new AtomicInteger(0);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @EndpointInject
    private ProducerTemplate producerTemplate;

    @Autowired
    Environment environment;

    /**
     * 组装事件消息
     *
     * @param body
     * @param currentCommandJson
     * @return
     */
    @Consume("eventstore://get-event-message")
    public EventMessage getEventMessage(@Body String body, @Header("currentCommandJson") String currentCommandJson) {
        EventMessage eventMessage = new EventMessage();
        eventMessage.setContent(currentCommandJson);
        eventMessage.setRegion(body);
        eventMessage.setStatus("WAITING");
        return eventMessage;
    }

    /**
     * 发送消息
     *
     * @param object
     * @param mappings
     */
    @Consume("publish://sending-message")
    public void send(
            @Body Object object,
            @Header("mappings") List<EventsInfo.EventMapping> mappings,
            @Header("eventInfo") EventsInfo.Event eventInfo
    ) {
        HashMap eventObject = JsonUtil.writeObjectAsObject(object, HashMap.class);
        String content = (String) eventObject.get("content");
        Long messageId = (Long) eventObject.get("messageId");
        Object groupId = eventObject.get("groupId");
        Map<String, EventsInfo.EventMapping> mappingMap = mappings.stream().collect(Collectors.toMap(EventsInfo.EventMapping::getReceiverAddress, i -> i, (i1, i2) -> i1));

//        // todo，test，模拟1/10发送失败情况
//        if (0 == System.currentTimeMillis() % 10) {
//            // 发送失败 则 记录错误信息/累加错误次数
//            ErrorMessage errorMessage = constructErrorMessage(eventInfo, content, messageId, mappingMap, "mock producer error");
//            producerTemplate.requestBody("publish://error-sending-resolve", errorMessage);
//            log.warn("发送失败（模拟）: " + messageId);
//            return;
//        }

        Message<?> objectMessage = MessageBuilder
                .withPayload(content)
                .setHeader(KafkaHeaders.TOPIC, eventInfo.getTopicName())
                .setHeader("groupId", "".equals(groupId) ? null : groupId) // 发送错误消息全发，死信重发时指定GROUP
                .setHeader("context", environment.getProperty("spring.application.name"))
                .setHeader("messageId", messageId)
                .setHeader("sender", eventInfo.getSenderAddress())
                .setHeader("event", eventInfo.getEventName())
                .setHeader("mappingMap", mappingMap).build();
        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(objectMessage);
        future.completable().whenCompleteAsync((sendResult, throwable) -> {
            // todo，test，记录第一次发送时间，累加发送次数，每100条打印用时
            if (startTime == null) {
                startTime = System.currentTimeMillis();
            }
            count.getAndIncrement();
            if (count.get() % 100 == 0) {
                long usedTime = System.currentTimeMillis() - startTime;
                log.info("发送速率：用时=" + usedTime + "，次数=" + count);
            }

            if (null == throwable) {
                // 发送成功 则 删除错误记录
                ErrorMessage errorMessage = new ErrorMessage();
                errorMessage.setMessageId(messageId);
                errorMessage.setGroupId((String) groupId);
                producerTemplate.requestBody("publish://success-sending-resolve", errorMessage);
                log.info("发送成功: " + messageId);
            } else {
                // 发送失败 则 记录错误信息/累加错误次数
                ErrorMessage errorMessage = constructErrorMessage(eventInfo, content, messageId, mappingMap, throwable.getLocalizedMessage());
                producerTemplate.requestBody("publish://error-sending-resolve", errorMessage);
                log.error("发送失败（真实）: " + messageId);
            }
        });
    }

    private ErrorMessage constructErrorMessage(EventsInfo.Event eventInfo, String content, Long messageId,
                                               Map<String, EventsInfo.EventMapping> mappingMap, String error) {
        ErrorMessage errorMessage = new ErrorMessage();
        errorMessage.setMessageId(messageId);
        errorMessage.setSender(eventInfo.getSenderAddress());
        errorMessage.setTopic(eventInfo.getTopicName());
        errorMessage.setEvent(eventInfo.getEventName());
        errorMessage.setMappingMap(JsonUtil.writeValueAsString(mappingMap));
        errorMessage.setContent(content);
        errorMessage.setGroupId("");
        errorMessage.setErrorCount(1);
        errorMessage.setErrorStatus("WAITING");
        errorMessage.setErrorMessage(error);
        return errorMessage;
    }

    /**
     * 修改跟踪器序号
     *
     * @param errorMessage
     * @return
     */
    @Consume("publish://error-sending-resolve-create")
    public ErrorMessage createErrorMessage(@Header("current") ErrorMessage errorMessage) {
        return errorMessage;
    }

    /**
     * 标记发送中
     *
     * @param errorMessage
     * @return
     */
    @Consume("publish://error-resolving")
    public ErrorMessage errorResolving(@Body ErrorMessage errorMessage) {
        errorMessage.setErrorStatus("SENDING");
        return errorMessage;
    }

    /**
     * 标记已发送
     *
     * @param eventMessage
     * @return
     */
    @Consume("publish://normal-resolving")
    public EventMessage normalResolving(@Body EventMessage eventMessage) {
        eventMessage.setStatus("SENT");
        return eventMessage;
    }

    /**
     * 修改跟踪器序号
     *
     * @param errorMessage
     * @return
     */
    @Consume("publish://error-sending-resolve-update")
    public ErrorMessage updateErrorMessage(@Body ErrorMessage errorMessage, @Header("current") ErrorMessage current) {
        errorMessage.setErrorCount(errorMessage.getErrorCount() + 1);
        errorMessage.setErrorStatus(current.getErrorStatus());
        errorMessage.setErrorMessage(current.getErrorMessage());
        return errorMessage;
    }

    /**
     * 组装幂等消息
     *
     * @param messageDetail
     * @return
     */
    @Consume("subscribe://idempotent-message")
    public IdempotentMessage idempotent(Map<String, Object> messageDetail) {
        IdempotentMessage idempotent = new IdempotentMessage();
        idempotent.setSender((String) messageDetail.get("sender"));
        idempotent.setSequence(Long.valueOf(messageDetail.get("messageId").toString()));
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
                .bean(EventsInfo.class, "getAllEvents")
                .split(new SplitList()).parallelProcessing()
                    .setProperty("eventInfo", body())
                    .bean(ErrorMessageRepository.class, "findErrorMessageByTopicAndSenderLimitByCount(${exchange.properties.get(eventInfo).getTopicName()},${exchange.properties.get(eventInfo).getSenderAddress()},100)")
                    .setHeader("errorMessageCount", simple("${body.size}"))
                    .setProperty("errorEvents", body())
                    .process(exchange -> {
                        Integer errorMessageCount = (Integer) exchange.getIn().getHeader("errorMessageCount");
                        exchange.getIn().setHeader("normalMessageCount", 1000 - errorMessageCount);
                    })
                    .bean(EventMessageRepository.class, "findEventMessageByRegionLimitByCount(${exchange.properties.get(eventInfo).getEventName()}, ${header.normalMessageCount})")
                    .setProperty("normalEvents", body())
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

        from("publish://send-normal")
                .transacted()
                .to("publish://normal-resolving")
                .bean(EventMessageRepository.class, "save")
                .to("publish://execute-send")
                .end();

        from("publish://send-error")
                .transacted()
                .to("publish://error-resolving")
                .bean(ErrorMessageRepository.class, "save")
                .to("publish://execute-send")
                .end();

        // 通用正常消息发送路由
        from("publish://execute-send")
                .routeId("direct://send-normal")
                .setHeader("normalMessage", body())
                .setHeader("mappings", simple("${exchange.properties.get(eventInfo).getEventMappings()}"))
                .setHeader("eventInfo", exchangeProperty("eventInfo"))
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
                .bean(ErrorMessageRepository.class, "save")
                .end();

        // 发送失败监听路由
        from("publish://error-sending-resolve")
                .routeId("publish://error-sending-resolve")
                .transacted()
                .setHeader("current", body())
                .bean(ErrorMessageRepository.class, "findFirstByMessageIdAndGroupId(${body.messageId}, ${body.groupId})")
                .choice()
                    .when(body().isNull())
                        .to("publish://error-sending-resolve-create")
                    .endChoice()
                    .otherwise()
                        .to("publish://error-sending-resolve-update")
                    .endChoice()
                .end()
                .bean(ErrorMessageRepository.class, "save")
                .end();

        // 发送成功监听路由
        from("publish://success-sending-resolve")
                .routeId("publish://success-sending-resolve")
                .transacted()
                .choice()
                    .when(simple("${body.groupId}").isNotNull()) // 说明是错误信息重发的
                        .bean(ErrorMessageRepository.class, "deleteByMessageIdAndGroupId(${body.messageId}, ${body.groupId})")
                    .endChoice()
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



