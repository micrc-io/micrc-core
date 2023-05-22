//package io.micrc.core.message.rabbit.tracking;
//
//import io.micrc.core.message.rabbit.RabbitMessageRouteConfiguration.EventsInfo.Event;
//import io.micrc.core.message.rabbit.store.RabbitEventMessage;
//import io.micrc.lib.JsonUtil;
//import lombok.Data;
//import org.apache.camel.Body;
//import org.apache.camel.Consume;
//import org.apache.camel.Header;
//import org.springframework.amqp.rabbit.connection.CorrelationData;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//
//import javax.persistence.Entity;
//import javax.persistence.Id;
//import javax.persistence.Table;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * 消息跟踪器
// *
// * @author tengwang
// * @date 2022/9/23 14:39
// * @since 0.0.1
// */
//@Data
//@Entity
//@Table(name = "rabbit_message_message_tracker")
//public class RabbitMessageTracker {
//
//    @Id
//    private String trackerId;
//
//    /**
//     * 消息通道
//     */
//    private String channel;
//
//    /**
//     * 交换区
//     */
//    private String exchange;
//
//    /**
//     * 事件类型
//     */
//    private String region;
//
//    /**
//     * 发送序列
//     */
//    private Long sequence;
//
//    @Consume("eventstore://create-tracker")
//    public RabbitMessageTracker create(Event event) {
//        RabbitMessageTracker tracker = new RabbitMessageTracker();
//        tracker.setChannel(event.getChannel());
//        tracker.setRegion(event.getEventName());
//        tracker.setExchange(event.getExchangeName());
//        tracker.setSequence(0L);
//        tracker.setTrackerId(event.getExchangeName() + "-" + event.getChannel());
//        return tracker;
//    }
//
//    @Consume("eventstore://tracker-move")
//    public RabbitMessageTracker move(@Body RabbitMessageTracker tracker, @Header("eventMessages") List<RabbitEventMessage> rabbitEventMessages) {
//        tracker.setSequence(rabbitEventMessages.get(rabbitEventMessages.size() - 1).getSequence());
//        return tracker;
//    }
//
//    @Consume("publish://sending")
//    public void send(
//            @Body RabbitEventMessage rabbitEventMessage,
//            @Header("template") RabbitTemplate template,
//            @Header("tracker") RabbitMessageTracker tracker,
//            @Header("type") String type
//    ) {
//        Map<String, Object> messageDetail = new HashMap<>();
//        messageDetail.put("exchange", tracker.getExchange());
//        messageDetail.put("channel", tracker.getChannel());
//        messageDetail.put("region", tracker.getRegion());
//        messageDetail.put("sequence", rabbitEventMessage.getSequence());
//        messageDetail.put("type", type);
//        CorrelationData correlationData = new CorrelationData(JsonUtil.writeValueAsString(messageDetail));
//        template.convertAndSend(tracker.getExchange(), tracker.getChannel(), rabbitEventMessage, correlationData);
//    }
//}
