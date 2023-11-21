package io.micrc.core.message;

import io.micrc.core.message.error.ErrorMessage;
import io.micrc.core.message.error.ErrorMessageRepository;
import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.message.store.IdempotentMessage;
import io.micrc.core.message.store.IdempotentMessageRepository;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 消息存储、发送路由，订阅路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 05:35
 * @since 0.0.1
 */
public class MessageRouteConfiguration extends RouteBuilder implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @EndpointInject
    private ProducerTemplate producerTemplate;

    @Autowired
    Environment environment;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 组装事件消息
     *
     * @param body                  body
     * @param currentCommandJson    currentCommandJson
     * @return                      EventMessage
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
     * @param object    object
     * @param mappings  mappings
     * @param eventInfo eventInfo
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
        Map<String, EventsInfo.EventMapping> mappingMap = mappings.stream().collect(Collectors.toMap(EventsInfo.EventMapping::getMappingKey, i -> i, (i1, i2) -> i1));

        Message<?> objectMessage = MessageBuilder
                .withPayload(content)
                .setHeader(KafkaHeaders.TOPIC, eventInfo.getTopicName())
                .setHeader("groupId", "".equals(groupId) ? null : groupId) // 发送错误消息全发，死信重发时指定GROUP
                .setHeader("senderHost", environment.getProperty("micrc.x-host"))
                .setHeader("messageId", messageId)
                .setHeader("sender", eventInfo.getSenderAddress())
                .setHeader("event", eventInfo.getEventName())
                .setHeader("mappingMap", mappingMap).build();

        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        KafkaTemplate<String, String> kafkaTemplate;
        if (profiles.contains("default")) {
            kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate.class);
        } else {
            String topicName = eventInfo.getTopicName();
            String provider = "public"; // todo,根据主题名称动态指定provider
            kafkaTemplate = applicationContext.getBean("kafkaTemplate-" + provider, KafkaTemplate.class);
        }

        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(objectMessage);
        future.completable().whenCompleteAsync((sendResult, throwable) -> {
            if (null == throwable) {
                // 发送成功 则 删除错误记录
                ErrorMessage errorMessage = new ErrorMessage();
                errorMessage.setMessageId(messageId);
                errorMessage.setGroupId((String) groupId);
                producerTemplate.requestBody("publish://success-sending-resolve", errorMessage);
                log.info("发送成功: " + messageId + "，是否死信" + (groupId != null));
            } else {
                // 发送失败 则 记录错误信息/累加错误次数
                ErrorMessage errorMessage = constructErrorMessage(eventInfo, content, messageId, mappingMap, throwable.getLocalizedMessage());
                producerTemplate.requestBody("publish://error-sending-resolve", errorMessage);
                log.error("发送失败: " + messageId + "，是否死信" + (groupId != null));
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
     * @param errorMessage  errorMessage
     * @return              ErrorMessage
     */
    @Consume("publish://error-sending-resolve-create")
    public ErrorMessage createErrorMessage(@Header("current") ErrorMessage errorMessage) {
        return errorMessage;
    }

    /**
     * 标记发送中
     *
     * @param errorMessage  errorMessage
     * @return              ErrorMessage
     */
    @Consume("publish://error-resolving")
    public ErrorMessage errorResolving(@Body ErrorMessage errorMessage) {
        errorMessage.setErrorStatus("SENDING");
        return errorMessage;
    }

    /**
     * 标记已发送
     *
     * @param eventMessage  eventMessage
     * @return              EventMessage
     */
    @Consume("publish://normal-resolving")
    public EventMessage normalResolving(@Body EventMessage eventMessage) {
        eventMessage.setStatus("SENT");
        return eventMessage;
    }

    /**
     * 修改跟踪器序号
     *
     * @param errorMessage  errorMessage
     * @param current       errorMessage
     * @return              ErrorMessage
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
     * @param messageDetail messageDetail
     * @return              IdempotentMessage
     */
    @Consume("subscribe://idempotent-message")
    public IdempotentMessage idempotent(Map<String, Object> messageDetail) {
        IdempotentMessage idempotent = new IdempotentMessage();
        idempotent.setSender((String) messageDetail.get("senderHost"));
        idempotent.setSequence(Long.valueOf(messageDetail.get("messageId").toString()));
        idempotent.setReceiver((String) messageDetail.get("serviceName"));
        return idempotent;
    }

    /**
     * 用幂等仓过滤ID
     *
     * @param messageIds    messageIds
     * @param eventInfo     eventInfo
     * @return              messageIds
     */
    @Consume("clean://idempotent-consumed-filter")
    public List<Long> filter(@Body List<Long> messageIds, @Header("eventInfo") EventsInfo.Event eventInfo) {
        List<Long> result = messageIds;
        List<EventsInfo.EventMapping> eventMappings = eventInfo.getEventMappings();
        for (int i = 0; i < eventMappings.size(); i++) {
            if (result.isEmpty()) {
                break;
            }
            EventsInfo.EventMapping eventMapping = eventMappings.get(i);
            HashMap<String, Object> body = new HashMap<>();
            body.put("messageIds", result);
            body.put("receiver", eventMapping.getMappingKey());
            String endpoint = "rest://post:/api/check-idempotent-consumed?host=" + spliceHost(eventMapping.receiverAddress);
            String response = producerTemplate.requestBodyAndHeaders(endpoint, JsonUtil.writeValueAsString(body), constructHeaders(), String.class);
            result = JsonUtil.writeValueAsList(response, Long.class);
        }
        if (!result.isEmpty()) {
            log.info("消息表清理：" + JsonUtil.writeValueAsString(result));
        }
        return result;
    }

    private HashMap<String, Object> constructHeaders() {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJwZXJtaXNzaW9ucyI6WyIqOioiXSwidXNlcm5hbWUiOiItMSJ9.N97J1cv1Io02TLwAekzOoDHRFrnGOYeXCUiDhbAYBYY");
        return headers;
    }

    private String spliceHost(String xHost) {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        String[] split = xHost.split("\\.");
        if (split.length != 3) {
            throw new RuntimeException("micrc.x-host invalid");
        }
        String product = split[0];
        String domain = split[1];
        String context = split[2];
        return "http://" + context + "-service." + product + "-" + domain + "-" + profiles.get(0) + ".svc.cluster.local";
    }

    /**
     * 用消息表过滤ID
     *
     * @param messageIds    messageIds
     * @param senderAddress senderAddress
     * @return              messageIds
     */
    @Consume("clean://store-removed-filter")
    public List<Long> filter(@Body List<Long> messageIds, @Header("senderAddress") String senderAddress) {
        if (messageIds.isEmpty()) {
            return messageIds;
        }
        HashMap<String, Object> body = new HashMap<>();
        body.put("messageIds", messageIds);
        String endpoint = "rest://post:/api/check-store-removed?host=" + spliceHost(senderAddress);
        String response = producerTemplate.requestBodyAndHeaders(endpoint, JsonUtil.writeValueAsString(body), constructHeaders(), String.class);
        List<Long> unRemoveIds = JsonUtil.writeValueAsList(response, Long.class);
        messageIds.removeAll(unRemoveIds);
        if (!messageIds.isEmpty()) {
            log.info("幂等仓清理：" + JsonUtil.writeValueAsString(messageIds));
        }
        return messageIds;
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

        from("eventstore://clear")
                .routeId("eventstore://clear")
                .transacted()
                .bean(EventsInfo.class, "getAllEvents")
                .split(new SplitList()).parallelProcessing()
                    .setHeader("eventInfo", body())
                    .bean(EventMessageRepository.class, "findSentIdByRegionLimitCount(${body.getEventName()},1000)")
                    .to("clean://idempotent-consumed-filter")
                    .choice()
                        .when(simple("${body.size} > 0"))
                            .bean(EventMessageRepository.class, "deleteAllByIdInBatch")
                        .endChoice()
                    .end()
                .end()
                .bean(IdempotentMessageRepository.class, "findSender")
                .split(new SplitList()).parallelProcessing()
                    .setHeader("senderAddress", body())
                    .bean(IdempotentMessageRepository.class, "findMessageIdsBySenderLimitCount(${body},1000)")
                    .to("clean://store-removed-filter")
                    .choice()
                        .when(simple("${body.size} > 0"))
                            .bean(IdempotentMessageRepository.class, "deleteAllBySequenceIn")
                        .endChoice()
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

        // 检查幂等仓是否已消费
        from("rest:post:check-idempotent-consumed")
                .routeId("rest:post:check-idempotent-consumed")
                .convertBodyTo(String.class).unmarshal().json(HashMap.class)
                .bean(IdempotentMessageRepository.class, "filterMessageIdByMessageIdsAndReceiver(${body.get(messageIds)}, ${body.get(receiver)})")
                .marshal().json().convertBodyTo(String.class)
                .end();

        // 检查消息存储表是否已删除
        from("rest:post:check-store-removed")
                .routeId("rest:post:check-store-removed")
                .convertBodyTo(String.class).unmarshal().json(HashMap.class)
                .bean(EventMessageRepository.class, "findUnRemoveIdsByMessageIds(${body.get(messageIds)})")
                .marshal().json().convertBodyTo(String.class)
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



