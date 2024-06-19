package io.micrc.core.message;

import io.micrc.core.message.error.ErrorMessage;
import io.micrc.core.message.error.ErrorMessageRepository;
import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.message.store.IdempotentMessageRepository;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.http.HttpHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;

import java.time.Duration;
import java.util.*;
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

    @Autowired
    EventMessageRepository eventMessageRepository;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @KafkaListener(topics = "deadLetter", autoStartup = "true", concurrency = "3", containerFactory = "kafkaListenerContainerFactory")
    public void deadLetter(ConsumerRecord<?, ?> consumerRecord, Acknowledgment acknowledgment) {
        try {
            HashMap<String, String> deadLetterDetail = new HashMap<>();
            Iterator<org.apache.kafka.common.header.Header> headerIterator = consumerRecord.headers().iterator();
            while (headerIterator.hasNext()) {
                org.apache.kafka.common.header.Header header = headerIterator.next();
                deadLetterDetail.put(header.key(), new String(header.value()));
            }
            if (deadLetterDetail.get("senderHost").equals(environment.getProperty("micrc.x-host"))) {
                ErrorMessage errorMessage = new ErrorMessage();
                errorMessage.setMessageId(Long.valueOf(deadLetterDetail.get("messageId")));
                errorMessage.setEvent(deadLetterDetail.get("event"));
                errorMessage.setContent(consumerRecord.value().toString());
                errorMessage.setGroupId(deadLetterDetail.get("kafka_dlt-original-consumer-group")); // 原始消费者组ID
                boolean isCopyEvent = Boolean.parseBoolean(deadLetterDetail.get("isCopyEvent"));
                if (isCopyEvent) {
                    errorMessage.setOriginalTopic(deadLetterDetail.get("kafka_dlt-original-topic"));
                    String mappingMap = deadLetterDetail.get("mappingMap");
                    HashMap hashMap = JsonUtil.writeValueAsObject(mappingMap, HashMap.class);
                    Object next = hashMap.values().iterator().next();
                    errorMessage.setOriginalMapping(JsonUtil.writeValueAsString(next));
                }
                errorMessage.setErrorCount(1);
                errorMessage.setErrorStatus("STOP");
                errorMessage.setErrorMessage(deadLetterDetail.get("kafka_dlt-exception-message")); // 异常信息
                producerTemplate.requestBody("publish://error-sending-resolve", errorMessage);
                log.info("死信保存: " + errorMessage.getMessageId());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            acknowledgment.nack(Duration.ofMillis(0L));
        }
    }

    /**
     * 发送消息
     *
     * @param object    object
     * @param eventInfo eventInfo
     * @param isCopyEvent isCopyEvent
     */
    @Consume("publish://sending-message")
    public void send(
            @Body Object object,
            @Header("eventInfo") EventsInfo.Event eventInfo,
            @Header("isCopyEvent") Boolean isCopyEvent
    ) {
        HashMap eventObject = JsonUtil.writeObjectAsObject(object, HashMap.class);
        String content = (String) eventObject.get("content");
        Long messageId = (Long) eventObject.get("messageId");
        Object groupId = eventObject.get("groupId");
        boolean isDeadLetter = StringUtils.hasText((String) groupId);
        Map<String, EventsInfo.EventMapping> mappingMap;
        if (isDeadLetter) {
            mappingMap = JsonUtil.writeValueAsObject(content, HashMap.class);
        } else {
            mappingMap = eventInfo.getEventMappings().stream()
                    .map(eventMapping -> EventsInfo.EventMapping.builder()
                            .mappingKey(eventMapping.getMappingKey())
                            .mappingPath(JsonUtil.transform(eventMapping.getMappingPath(), content))
                            .receiverAddress(eventMapping.getReceiverAddress())
                            .batchModel(eventMapping.getBatchModel()).build()
                    ).collect(Collectors.toMap(EventsInfo.EventMapping::getMappingKey, i -> i, (i1, i2) -> i1));
        }
        // for kafka stringSerializer
        String mappingMapContent = JsonUtil.writeValueAsString(mappingMap);
        Message<?> objectMessage = MessageBuilder
                .withPayload(mappingMapContent)
                .setHeader(KafkaHeaders.TOPIC, eventInfo.getTopicName())
                .setHeader("groupId", isDeadLetter ? groupId : null)
                .setHeader("senderHost", environment.getProperty("micrc.x-host"))
                .setHeader("messageId", messageId)
                .setHeader("isCopyEvent", isCopyEvent)
                .setHeader("event", eventInfo.getEventName())
                .setHeader("mappingMap", mappingMap).build();

        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        KafkaTemplate<String, String> kafkaTemplate = findKafkaTemplate(eventInfo, profiles);

        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(objectMessage);
        future.completable().whenCompleteAsync((sendResult, throwable) -> {
            resolveSendResult(eventInfo, isCopyEvent, throwable, messageId, groupId, content);
        });
    }

    private void resolveSendResult(EventsInfo.Event eventInfo, Boolean isCopyEvent, Throwable throwable, Long messageId, Object groupId, String content) {
        if (null == throwable) {
            // 发送成功 则 删除错误记录
            ErrorMessage errorMessage = new ErrorMessage();
            errorMessage.setMessageId(messageId);
            errorMessage.setGroupId((String) groupId);
            producerTemplate.requestBody("publish://success-sending-resolve", errorMessage);
            log.info("发送成功: " + messageId + "，是否死信" + (groupId != null));
        } else {
            // 发送失败 则 记录错误信息/累加错误次数
            ErrorMessage errorMessage = constructErrorMessage(eventInfo, content, messageId, isCopyEvent, throwable.getLocalizedMessage());
            producerTemplate.requestBody("publish://error-sending-resolve", errorMessage);
            log.error("发送失败: " + messageId + "，是否死信" + (groupId != null));
        }
    }

    @NotNull
    private KafkaTemplate<String, String> findKafkaTemplate(EventsInfo.Event eventInfo, List<String> profiles) {
        KafkaTemplate<String, String> kafkaTemplate;
        if (profiles.contains("default") || profiles.contains("local")) {
            kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate.class);
        } else {
            String topicName = eventInfo.getTopicName();
            Properties properties = (Properties)((ConfigurableEnvironment)environment).getPropertySources().get("micrc").getSource();
            String provider = properties.entrySet().stream().filter(entry -> {
                if (entry.getKey().toString().startsWith("micrc.broker.topics.")) {
                    String[] split = entry.getValue().toString().split(",");
                    return Arrays.asList(split).contains(topicName);
                }
                return false;
            }).map(entry -> {
                String[] split = entry.getKey().toString().split("micrc\\.broker\\.topics\\.");
                return split[split.length - 1];
            }).findFirst().orElseThrow();
            if ("public".equalsIgnoreCase(provider)) {
                provider = "";
            } else {
                provider = "-" + provider;
            }
            kafkaTemplate = applicationContext.getBean("kafkaTemplate" + provider, KafkaTemplate.class);
        }
        return kafkaTemplate;
    }

    private ErrorMessage constructErrorMessage(EventsInfo.Event eventInfo, String content, Long messageId, Boolean isCopyEvent, String error) {
        ErrorMessage errorMessage = new ErrorMessage();
        errorMessage.setMessageId(messageId);
        errorMessage.setEvent(eventInfo.getEventName());
        errorMessage.setContent(content);
        errorMessage.setGroupId("");
        if (isCopyEvent != null && isCopyEvent) {
            errorMessage.setOriginalTopic(eventInfo.getTopicName());
            errorMessage.setOriginalMapping(JsonUtil.writeValueAsString(eventInfo.getEventMappings().get(0)));
        }
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
            if (response.startsWith("{")) {
                response = "[]"; // HTTP调用错误并直接返回入参作为响应，需特殊处理
            }
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
            throw new RuntimeException("x-host invalid");
        }
        String product = split[0];
        String domain = split[1];
        String context = split[2];
        if (domain.equals(environment.getProperty("micrc.domain"))
                && context.equals(Objects.requireNonNull(environment.getProperty("spring.application.name")).replace("-service", ""))) {
            return "http://localhost:" + environment.getProperty("local.server.port");
        }
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
        if (response.startsWith("{")) {
            response = JsonUtil.writeValueAsString(messageIds); // HTTP调用错误并直接返回入参作为响应，需特殊处理
        }
        List<Long> unRemoveIds = JsonUtil.writeValueAsList(response, Long.class);
        messageIds.removeAll(unRemoveIds);
        if (!messageIds.isEmpty()) {
            log.info("幂等仓清理：" + JsonUtil.writeValueAsString(messageIds));
        }
        return messageIds;
    }

    @Override
    public void configure() throws Exception {

        onException(PessimisticLockingFailureException.class)
                .handled(true);

        // 通用消息存储路由
        from("eventstore://store")
                .routeId("eventstore://store")
                .transacted()
                .process(exchange -> {
                    String content = exchange.getIn().getBody(String.class);
                    String eventName = (String) JsonUtil.readPath(content, "/event/eventName");
                    if (eventName != null && EventsInfo.getAllEvents().stream().anyMatch(event -> eventName.equals(event.getEventName()))) {
                        EventMessage eventMessage = new EventMessage();
                        eventMessage.setContent(content);
                        eventMessage.setRegion(eventName);
                        eventMessage.setStatus("WAITING");
                        eventMessageRepository.save(eventMessage);
                    }
                });

        // 正常消息发送路由
        from("publish://send-normal")
                .routeId("publish://send-normal")
                .transacted()
                .to("publish://normal-resolving")
                .bean(EventMessageRepository.class, "save")
                .setHeader("eventInfo", exchangeProperty("eventInfo"))
                .to("publish://sending-message")
                .end();

        // 错误消息发送路由
        from("publish://send-error")
                .routeId("publish://send-error")
                .transacted()
                .to("publish://error-resolving")
                .bean(ErrorMessageRepository.class, "save")
                .setHeader("eventInfo", exchangeProperty("eventInfo"))
                .to("publish://sending-message")
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

        // 调度发送主路由
        from("eventstore://sender")
                .routeId("eventstore://sender")
                // 需要发送的事件
                .bean(EventsInfo.class, "getAllEvents")
                .split(new SplitList()).parallelProcessing()
                    .setProperty("eventInfo", body())
                    .bean(ErrorMessageRepository.class, "findErrorMessageByEventLimitByCount(${exchange.properties.get(eventInfo).getEventName()}, 10)")
                    .setHeader("errorMessageCount", simple("${body.size}"))
                    .setProperty("errorEvents", body())
                    .process(exchange -> {
                        Integer errorMessageCount = (Integer) exchange.getIn().getHeader("errorMessageCount");
                        exchange.getIn().setHeader("normalMessageCount", 100 - errorMessageCount);
                    })
                    .bean(EventMessageRepository.class, "findEventMessageByRegionLimitByCount(${exchange.properties.get(eventInfo).getEventName()}, ${header.normalMessageCount})")
                    .setProperty("normalEvents", body())
                    .setBody(exchangeProperty("errorEvents"))
                    .process(exchange -> {
                        try {
                            Map<String, Object> properties = exchange.getProperties();
                            EventsInfo.Event eventInfo = (EventsInfo.Event) properties.get("eventInfo");
                            List normalEvents = (List) properties.get("normalEvents");
                            List errorEvents = (List) properties.get("errorEvents");
                            int normalEventsSize = normalEvents.size();
                            int errorEventsSize = errorEvents.size();
                            if (normalEventsSize > 0 || errorEventsSize > 0) {
                                log.info("调度发送{}, 正常数量{}, 错误数量{}", eventInfo.getEventName(), normalEventsSize, errorEventsSize);
                            }
                        } catch (Exception e) {
                            log.error("调度统计错误！");
                        }
                    })
                    .split(new SplitList()).parallelProcessing()
                        .to("publish://send-error")
                        .end()
                    .setBody(exchangeProperty("normalEvents"))
                    .split(new SplitList()).parallelProcessing()
                        .to("publish://send-normal")
                        .end()
                    .end()
                // 接收的需要复制自重发的事件
                .setHeader("isCopyEvent", constant(true))
                .bean(EventMessageRepository.class, "findEventMessageByOriginalExists()")
                .split(new SplitList()).parallelProcessing()
                    .process(exchange -> {
                        EventMessage eventMessage = exchange.getIn().getBody(EventMessage.class);
                        EventsInfo.EventMapping eventMapping = JsonUtil.writeValueAsObject(eventMessage.getOriginalMapping(), EventsInfo.EventMapping.class);
                        EventsInfo.Event event = EventsInfo.Event.builder()
                                .topicName(eventMessage.getOriginalTopic())
                                .eventName(eventMessage.getRegion())
                                .eventMappings(Arrays.asList(eventMapping)).build();
                        exchange.setProperty("eventInfo", event);
                    })
                    .to("publish://send-normal")
                    .end()
                .bean(ErrorMessageRepository.class, "findErrorMessageByOriginalExists()")
                .split(new SplitList()).parallelProcessing()
                    .process(exchange -> {
                        ErrorMessage errorMessage = exchange.getIn().getBody(ErrorMessage.class);
                        EventsInfo.EventMapping eventMapping = JsonUtil.writeValueAsObject(errorMessage.getOriginalMapping(), EventsInfo.EventMapping.class);
                        EventsInfo.Event event = EventsInfo.Event.builder()
                                .topicName(errorMessage.getOriginalTopic())
                                .eventName(errorMessage.getEvent())
                                .eventMappings(Arrays.asList(eventMapping)).build();
                        exchange.setProperty("eventInfo", event);
                    })
                    .to("publish://send-error")
                    .end()
                .end();

        from("eventstore://clear")
                .routeId("eventstore://clear")
                .transacted()
                // 发送出去的事件都被消费则清理
                .bean(EventsInfo.class, "getAllEvents")
                .split(new SplitList())
                    .setHeader("eventInfo", body())
                    .bean(EventMessageRepository.class, "findSentIdByRegionLimitCount(${body.getEventName()},100)")
                    .to("clean://idempotent-consumed-filter")
                    .choice()
                        .when(simple("${body.size} > 0"))
                        .bean(EventMessageRepository.class, "deleteAllByIdInBatch")
                        .endChoice()
                    .end()
                .end()
                // 在接收方复制并发送的事件被消费则清理
                .bean(EventMessageRepository.class, "findSentIdByOriginalExists()")
                .split(new SplitList())
                    .process(exchange -> {
                        EventMessage eventMessage = exchange.getIn().getBody(EventMessage.class);
                        EventsInfo.EventMapping eventMapping = JsonUtil.writeValueAsObject(eventMessage.getOriginalMapping(), EventsInfo.EventMapping.class);
                        MessageRouteConfiguration.EventsInfo.Event event = EventsInfo.Event.builder()
                                .topicName(eventMessage.getOriginalTopic())
                                .eventName(eventMessage.getRegion())
                                .eventMappings(Arrays.asList(eventMapping)).build();
                        exchange.getIn().setHeader("eventInfo", event);
                    })
                    .to("clean://idempotent-consumed-filter")
                    .choice()
                        .when(simple("${body.size} > 0"))
                            .bean(EventMessageRepository.class, "deleteAllByIdInBatch")
                        .endChoice()
                    .end()
                .end()
                // 接收到的事件已被删除的则清理幂等仓
                .bean(IdempotentMessageRepository.class, "findSender")
                .split(new SplitList())
                    .setHeader("senderAddress", body())
                    .bean(IdempotentMessageRepository.class, "findMessageIdsBySenderLimitCount(${body},100)")
                    .to("clean://store-removed-filter")
                    .choice()
                        .when(simple("${body.size} > 0"))
                        .bean(IdempotentMessageRepository.class, "deleteAllBySequenceIn")
                        .endChoice()
                    .end()
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

        private static final List<Event> eventsCache = new ArrayList<>();
        private static final HashMap<String, Event> eventsInfo = new HashMap<>();

        public static void put(String key, Event value) {
            eventsInfo.put(key, value);
            eventsCache.add(value);
        }

        public static List<Event> getAllEvents() {
            return eventsCache;
        }

        public static Event get(String key) {
            return eventsInfo.get(key);
        }

        @Data
        @SuperBuilder
        public static class Event {

            /**
             * 发送主题名称
             */
            final private String topicName;

            /**
             * 事件名称
             */
            final private String eventName;

            /**
             * 消息映射
             */
            final private List<EventMapping> eventMappings;

        }

        @Data
        @NoArgsConstructor
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

            /**
             * 批量概念
             */
            private String batchModel;
        }
    }

}



