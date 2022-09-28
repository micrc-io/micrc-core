package io.micrc.core.message;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.message.jpa.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.AggregationStrategy;
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

    public static final String ROUTE_TMPL_MESSAGE_SUBSCRIBER =
            MessageRouteConfiguration.class.getName() + ".messageSubscriber";

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

        from("eventstore://sender")
                .routeId("eventstore://sender")
                .transacted()
                .bean(EventsInfo.class, "getAllEvents")
                .split(new SplitList()).parallelProcessing()
                .setProperty("eventInfo", body())
                .bean(MessageTrackerRepository.class, "findFirstByChannel(${body.getChannel()})")
                .choice().when(body().isNull())
                .setBody(exchangeProperty("eventInfo"))
                .to("eventstore://create-tracker")
                .log("tracker创建完成")
                .endChoice()
                .end()
                .setProperty("currentTracker", body())
                .log("开始获取异常消息")
                .bean(ErrorMessageRepository.class, "findErrorMessageByExchangeAndChannelLimitByCount(${exchange.properties.get(currentTracker).getExchange()}, ${exchange.properties.get(currentTracker).getChannel()}, 100)")
                .setHeader("errorMessageCount", simple("${body.size}"))
                .setProperty("errorEvents", body())
                .log("开始获取正常消息")
                .process(exchange -> {
                    Integer errorMessageCount = (Integer) exchange.getIn().getHeader("errorMessageCount");
                    exchange.getIn().setHeader("normalMessageCount", 100 - errorMessageCount);
                })
                .bean(EventMessageRepository.class, "findEventMessageByRegionAndCurrentSequenceLimitByCount(${exchange.properties.get(currentTracker).getRegion()}, ${exchange.properties.get(currentTracker).getSequence()}, ${header.normalMessageCount})")
                .setProperty("normalEvents", body())
                .choice().when(simple("${body.size} > 0"))
                .setBody(exchangeProperty("currentTracker"))
                .setHeader("eventMessages", simple("${exchange.properties.get(normalEvents)}"))
                .to("eventstore://tracker-move")
                //.bean(MessageTracker.class, "moveSequence(${exchange.properties.get(currentTracker)}, ${exchange.properties.get(normalEvents)})")
                .bean(MessageTrackerRepository.class, "save")
                .endChoice()
                .end()
                .setBody(exchangeProperty("errorEvents"))
                .split(new SplitList()).parallelProcessing()
                .to("publish://send-error")
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
        from("direct://send-normal") // publish
                .log("开始发送消息")
                .log("进行参数转换适配")
                .log("使用spring rabbit客户端,发送消息,消息头部放置交换区-队列-逻辑名称")
                .log("开启事务")
                .log("得到消息发送结果成功的时候,当发送的消息为失败重发消息时,回删关联表的消息, 当发送的消息为新发时,则记录该通道已发送成功,在该通道成功的同时,查看所有该事件的tracker的发送序列是否均比当前大,如均比当前大,则标记该消息已经发送完成,否则什么都不做")
                .log("得到消息发送结果失败的时候,当发送的消息为失败重发消息时,关联表记录重发失败次数, 当发送的消息为新发时,则入库Tracker失败关联表,并记录原因为发送失败")
                .log("提交事务")
                .end();
        // TODO 事件订阅路由模版，鉴别消息的事件类型，执行幂等，调用integration中的消息监听适配协议（message）使用subscribe协议（spring-rabbitmq组件）监听
        routeTemplate(ROUTE_TMPL_MESSAGE_SUBSCRIBER)
                .templateParameter("adapterName", null, "the message adapter name")
                .templateParameter("exchangeName", null, "the message exchange name")
                .templateParameter("eventName", null, "the event name")
                .templateParameter("logicName", null, "the businesses logic name")
                .templateParameter("ordered", null, "the event ordered")
                //.from("direct://{{exchangeName}}?queues={{eventName}}-{{logicName}}")// subscribe
                .from("subscribe://{{exchangeName}}-{{eventName}}-{{logicName}}")// subscribe
                .transacted()// 事务开启
                .log("开启事务")
                .log("幂等消费者,处理消息是否已消费")
                .log("发送至消息适配器")
                .log("检查结果对象是否存在异常,如不存在异常则提交事务,否则回滚事务")
                .log("提交事务")
                .log("针对检查结果,不存在异常则对消息进行ack,否则ack失败")
                .end();

        // 死信监听路由,落盘本地Tracker关联表
        from("subscribe://dead-message")
                .transacted()
                .to("eventstore://dead-message-store")
                .bean(ErrorMessageRepository.class, "save")
                .end();

        // 发送失败监听路由,落盘本地Tracker关联表
        from("publish://error-sending-resolve")
                .transacted()
                .log("发送失败监听路由,落盘本地Tracker关联表")
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
        // 发送成功监听路由,如发送的是异常消息需要移除异常表数据
        from("publish://success-sending-resolve")
                .transacted()
                .choice()
                .when(header("type").isEqualTo("ERROR"))
                    .bean(ErrorMessageRepository.class, "deleteByExchangeAndChannelAndSequenceAndRegion(${body.get(exchange)}, ${body.get(channel)}, ${body.get(sequence)}, ${body.get(region)})")
                .end();
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class MessageDefinition extends AbstractRouteTemplateParamDefinition {

        /**
         * 消息适配器名称
         */
        private String adapterName;

        /**
         * 交换区名称
         */
        private String exchangeName;

        /**
         * 事件名称
         */
        private String eventName;

        /**
         * 逻辑名称
         */
        private String logicName;

        /**
         * 是否顺序消费
         */
        private Boolean ordered;
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


    public static class TrackerStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                List<MessageTracker> trackers = new ArrayList<>();
                trackers.add((MessageTracker) newExchange.getIn().getBody());
                newExchange.setProperty("trackers", trackers);
                newExchange.getIn().setBody(trackers);
                return newExchange;
            }
            // 此处不可使用ClassCastUtils 原因是其是多线程的,并不安全,如对其进行替换会导致其他线程异常
            @SuppressWarnings("unchecked")
            List<MessageTracker> trackers = (List<MessageTracker>) oldExchange.getProperty("trackers");
            trackers.add((MessageTracker) newExchange.getIn().getBody());
            oldExchange.getIn().setBody(trackers);
            return oldExchange;
        }
    }

    public static class MessageStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            List<EventMessage> eventMessages = (List<EventMessage>) newExchange.getIn().getHeader("events");
            MessageTracker tracker = (MessageTracker) newExchange.getIn().getHeader("tracker");
            @SuppressWarnings("unchecked")
            Map<String, EventsInfo.Event> eventInfo = (Map<String, EventsInfo.Event>) newExchange.getProperty("eventsInfo");
            // 此处不可使用ClassCastUtils 原因是其是多线程的,并不安全,如对其进行替换会导致其他线程异常
            Map<String, Object> channelEvent = getMessageMap(tracker, eventInfo, eventMessages);
            if (oldExchange == null) {
                Map<String, Object> messageMap = new HashMap<>();
                newExchange.setProperty("messages", messageMap);
                messageMap.put(tracker.getChannel(), channelEvent);
                newExchange.getIn().setBody(messageMap);
                return newExchange;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = (Map<String, Object>) oldExchange.getProperty("messages");
            messageMap.put(tracker.getChannel(), channelEvent);
            oldExchange.getIn().setBody(messageMap);
            return oldExchange;
        }

        private Map<String, Object> getMessageMap(MessageTracker tracker, Map<String, EventsInfo.Event> eventInfo, List<EventMessage> events) {
            // 构造带适配的消息
            Map<String, Object> channelEvent = new HashMap<>();
            channelEvent.put("exchange", eventInfo.get(tracker.getChannel()).getExchangeName());
            channelEvent.put("channel", tracker.getChannel());
            channelEvent.put("mapping", eventInfo.get(tracker.getChannel()).getMappingPath());
            channelEvent.put("ordered", eventInfo.get(tracker.getChannel()).getOrdered());
            channelEvent.put("events", events);
            return channelEvent;
        }
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



