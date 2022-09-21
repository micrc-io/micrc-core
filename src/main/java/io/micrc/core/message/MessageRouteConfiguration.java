package io.micrc.core.message;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.builder.RouteBuilder;

import java.util.HashMap;

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
                .bean(StoredEvent.class, "store(${exchange.properties.get(commandJson)}, ${in.body})")
                .setHeader("event", body())
                .setBody(simple("insert into message_message_store (message_id, create_time, content, region) values ('${in.header.event.messageId}', ${in.header.event.createTime}, '${in.header.event.content}', '${in.header.event.region}')"))
                .to("jdbc:datasource?useHeadersAsParameters=true")
                .end();
        // TODO 初始化tracker(?需决定是前置创建还是后置创建还是变成一个项目启动完成后的过程检查,更建议在启动完成后只执行一次,能够优化主调度执行效率,因为运行过程中,tracker数量是不会发生变化的)
        from("eventstore://init")
                .log("检查global tracker数是否为1条,不为1条时进行创建")
                .setBody(constant("select count from message_message_tracker where tracker_type = 'GLOBAL'"))
                .to("jdbc:datasource")
                .choice()
                .when(simple("${in.body} != 1"))
                .log("查询当前Tracker表内的error tracker总条数是否为全局对象中的通道数量")
                .log("当error tracker与全局对象中的通道数量不相符时,找到不存在的通道并进customer行初始化error tracker")
                .end();

        // TODO 调度转发消息通用路由，从事件表中获取消息，使用消息跟踪表控制发送，使用publish协议（spring-rabbitmq组件）发送
        from("eventstore://sender")
                .log("the message sender start on ${in.body}")
                .log("调用初始化Tracker路由,从而决定是否初始化Tracker(要考虑新增Event的情况自动添加)")
                //.to("eventstore://init")
                .log("获取所有需要发送的信息,一次获取设定阈值(1000)条")
                .log("split-按照消息类型分组,已事件名称为Key进行分组")
                .log("split-按照通道拆分分组,并得到其映射文件及通道地址")
                .log("split-获取已发送失败的待重发消息,按照发送失败的通道与正常消息通道合并并放在头部")
                .log("split-按照通道里的每条消息进行发送")
                .log("开启事务")
                .log("aggr通道发送结果,当所有通道均发送成功时,标识message_store里的消息为已发送")
                .log("移动当次的全局Tracker的序列为获取的最后序列")
                .log("提交事务")
                .end();

        // TODO 通用消息发送路由
        from("direct://send") // publish
                .log("开始发送消息")
                .log("进行参数转换适配")
                .log("使用spring rabbit客户端,发送消息,消息头部放置交换区-队列-逻辑名称")
                .log("开启事务")
                .log("得到消息发送结果成功的时候,当发送的消息为失败重发消息时,回删关联表的消息, 当发送的消息为新发时,则记录该通道已发送成功")
                .log("得到消息发送结果失败的时候,当发送的消息为失败重发消息时,关联表记录重发失败次数, 当发送的消息为新发时,则入库死信关联表,并标识为待重发")
                .log("提交事务")
                .end();
        // TODO 事件订阅路由模版，鉴别消息的事件类型，执行幂等，调用integration中的消息监听适配协议（message）使用subscribe协议（spring-rabbitmq组件）监听
        routeTemplate(ROUTE_TMPL_MESSAGE_SUBSCRIBER)
                .templateParameter("adapterName", null, "the message adapter name")
                .templateParameter("exchangeName", null, "the message exchange name")
                .templateParameter("eventName", null, "the event name")
                .templateParameter("logicName", null, "the businesses logic name")
                .templateParameter("ordered", null, "the event ordered")
                .from("subscribe://{{exchangeName}}?queues={{eventName}}-{{logicName}}")// subscribe
                .transacted()// 事务开启
                .log("开启事务")
                .log("幂等消费者,处理消息是否已消费")
                .log("发送至消息适配器")
                .log("检查结果对象是否存在异常,如不存在异常则提交事务,否则回滚事务")
                .log("提交事务")
                .log("针对检查结果,不存在异常则对消息进行ack,否则ack失败")
                .end();

        // TODO 死信监听路由,落盘本地Tracker关联表
        from("subscribe://dead-message?queues=error")
                .log("获取当前消息的消息体以及消息头")
                .log("开启事务")
                .log("记录该消息是哪个通道的消息并存储至Tracker关联表,并标识为不可重发(此处的Tracker为异常tracker.正常序列跟踪只有全局Tracker一个实例)")
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

        private HashMap<String, Event> eventInfo = new HashMap<>();

        public EventsInfo(EventsInfo eventsInfo) {
            this.eventInfo = eventsInfo.eventInfo;
        }

        public void put(String key, Event value) {
            this.eventInfo.put(key, value);
        }

        public Event get(String key) {
            return eventInfo.get(key);
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
