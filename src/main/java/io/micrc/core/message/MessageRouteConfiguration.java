package io.micrc.core.message;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息存储、发送路由，订阅路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 05:35
 * @since 0.0.1
 */
public class MessageRouteConfiguration extends RouteBuilder {

    public static final String ROUTE_TMPL_MESSAGE_SUBSCRIBER =
            MessageRouteConfiguration.class.getName() + ".messageSubscriber";

    @Override
    public void configure() throws Exception {
        // 通用消息存储路由
        from("eventstore://store")
                .setHeader("pointer", constant("/event"))
                .to("json-patch://select")
                .bean(EventMessage.class, "store(${exchange.properties.get(commandJson)}, ${in.body})")
                //.bean(MessageRepository.class, "save")
                .setHeader("event", body())
                .setBody(simple("insert into message_message_store (message_id, create_time, content, region) values ('${in.header.event.messageId}', ${in.header.event.createTime}, '${in.header.event.content}', '${in.header.event.region}')"))
                .to("spring-jdbc:datasource?useHeadersAsParameters=true&resetAutoCommit=false")
                .end();

        // TODO 调度转发消息通用路由，从事件表中获取消息，使用消息跟踪表控制发送，使用publish协议（spring-rabbitmq组件）发送
        from("eventstore://sender")
                .log("the message sender start on ${in.body}")
                .log("spilt-获取所有需要发送的信息,读取内存对象的事件类型,按照事件类型的通道分通道每通道获取{计算出来的值-暂时先100}条-按照消息类型分组,以事件名称为Key进行分组-按照通道拆分分组,并得到其映射文件及通道地址")
                .to("eventstore://prepare-new-message")
                .log("split-获取已发送失败的待重发消息(按照通道臃肿比获取计算要取通道的实际消息),按照发送失败的通道与正常消息通道合并并放在头部")
                .to("eventstore://prepare-error-message")
                .process(exchange -> {
                    System.out.println(exchange);
                })
                .log("split-按照通道里的每条消息进行发送")
                .end();

        // 事件信息获取及Tracker创建路由
        from("eventstore://prepare-error-message")
                .log("抓异常消息?按通道抓还是直接顺序的抓一定的量")
                .end();

        from("eventstore://prepare-new-message")
                .bean(EventsInfo.class, "getAll")
                .setProperty("eventsInfo", body())
                .bean(EventsInfo.class, "getAllChannel")
                .split(body().tokenize(","), new TrackerStrategy()).parallelProcessing()
                .to("eventstore://fetch-tracker")
                .end()
                .setBody(exchangeProperty("trackers"))
                .transacted()
                .split(new SplitList(), new MessageStrategy()).parallelProcessing()
                .to("eventstore://fetch-new-message")
                .end()
                .setBody(exchangeProperty("trackers"))
                .split(new SplitList(), new MessageStrategy()).parallelProcessing()
                .setBody(simple("update message_message_tracker set sequence = '${body.getSequence()}' where channel = '${body.getChannel()}'"))
                .to("spring-jdbc:datasource?useHeadersAsParameters=true&outputType=SelectList&resetAutoCommit=false")
                .end()
                .setBody(exchangeProperty("messages"))
                .end();

        from("eventstore://fetch-new-message")
                .setHeader("tracker", body())
                .setBody(simple("select * from message_message_store where region = '${body.getRegion()}' and sequence > ${body.getSequence()} limit 100"))
                .to("spring-jdbc:datasource?useHeadersAsParameters=true&outputType=SelectList&resetAutoCommit=false")
                .setHeader("messagesCount", header("CamelJdbcRowCount"))
                // TODO 修正在H2下String变为Clob问题, 先使用临时替代方案
                .to("eventstore://convert-clob")
                .setHeader("events", body())
                .bean(MessageTracker.class, "moveSequence(${header.tracker}, ${header.messagesCount})")
                //.toD("eventstore://tracker-move?tracker=${header.tracker}&sequence=${header.messagesCount}")
                .end();

        from("eventstore://fetch-tracker")
                .setHeader("channel", body())
                .setBody(simple("select * from message_message_tracker where channel = :?channel"))
                .to("spring-jdbc:datasource?useHeadersAsParameters=true&outputClass=io.micrc.core.message.MessageTracker&outputType=SelectOne&resetAutoCommit=false")
                .choice().when(body().isNull())
                .setBody(header("channel"))
                .to("eventstore://create-tracker")
                .setHeader("currentTracker", body())
                .setBody(simple("insert into message_message_tracker (channel, region, sequence) values ('${body.channel}', '${body.region}', ${body.sequence})"))
                .to("jdbc:datasource")
                .setBody(header("currentTracker"))
                .log("tracker创建完成")
                .endChoice()
                .end()
                .end();

        // TODO 通用消息发送路由
        from("direct://send") // publish
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
                .from("direct://{{exchangeName}}")// subscribe
                .transacted()// 事务开启
                .log("开启事务")
                .log("幂等消费者,处理消息是否已消费")
                .log("发送至消息适配器")
                .log("检查结果对象是否存在异常,如不存在异常则提交事务,否则回滚事务")
                .log("提交事务")
                .log("针对检查结果,不存在异常则对消息进行ack,否则ack失败")
                .end();

        // TODO 死信监听路由,落盘本地Tracker关联表
        //from("direct://dead-message?queues=error")
        from("direct://dead-message")
                .log("获取当前消息的消息体以及消息头")
                .log("开启事务")
                .log("记录该消息是哪个通道的消息并存储至Tracker失败关联表,并记录原因为消费失败")
                .log("提交事务")
                .log("ack应答死信已消费")
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

        private List<String> channelsCache = new ArrayList<>();
        private HashMap<String, Event> eventsInfo = new HashMap<>();

        public EventsInfo(EventsInfo eventsInfo) {
            this.eventsInfo = eventsInfo.eventsInfo;
            this.channelsCache = eventsInfo.channelsCache;
        }

        public void put(String key, Event value) {
            this.eventsInfo.put(key, value);
            this.channelsCache.add(value.channel);
        }

        public Event get(String key) {
            return eventsInfo.get(key);
        }

        public Map<String, Event> getAll() {
            return this.eventsInfo;
        }

        public List<String> getAllChannel() {
            return this.channelsCache;
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

    public static class TrackerStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                newExchange.setProperty("trackers", new ArrayList<>());
                oldExchange = newExchange;
            }
            // 此处不可使用ClassCastUtils 原因是其是多线程的,并不安全,如对其进行替换会导致其他线程异常
            @SuppressWarnings("unchecked")
            List<MessageTracker> trackers = (List<MessageTracker>) newExchange.getProperty("trackers");
            trackers.add((MessageTracker) oldExchange.getIn().getBody());
            return oldExchange;
        }
    }

    public static class MessageStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                newExchange.setProperty("messages", new HashMap<>());
                oldExchange = newExchange;
            }
            // 此处不可使用ClassCastUtils 原因是其是多线程的,并不安全,如对其进行替换会导致其他线程异常
            @SuppressWarnings("unchecked")
            List<EventMessage> eventMessages = (List<EventMessage>) newExchange.getIn().getHeader("events");
            MessageTracker tracker = (MessageTracker) newExchange.getIn().getHeader("tracker");
            @SuppressWarnings("unchecked")
            Map<String, EventsInfo.Event> eventInfo = (Map<String, EventsInfo.Event>) newExchange.getProperty("eventsInfo");
            // 构造带适配的消息
            Map<String, Object> channelEvent = new HashMap<>();
            channelEvent.put("exchange", eventInfo.get(tracker.getChannel()).getExchangeName());
            channelEvent.put("channel", tracker.getChannel());
            channelEvent.put("mapping", eventInfo.get(tracker.getChannel()).getMappingPath());
            channelEvent.put("ordered", eventInfo.get(tracker.getChannel()).getOrdered());
            channelEvent.put("events", eventMessages);
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = (Map<String, Object>) newExchange.getProperty("messages");
            messageMap.put(tracker.getChannel(), channelEvent);
            return oldExchange;
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
            return objects.get(exchange.getProperty("CamelSplitIndex", Integer.class));
        }
    }
}


