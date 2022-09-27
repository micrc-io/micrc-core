package io.micrc.core.message.jpa;

import io.micrc.core.framework.json.JsonUtil;
import io.micrc.core.message.MessageRouteConfiguration.EventsInfo.Event;
import lombok.Data;
import org.apache.camel.Body;
import org.apache.camel.Consume;
import org.apache.camel.Header;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.List;

/**
 * 消息跟踪器
 *
 * @author tengwang
 * @date 2022/9/23 14:39
 * @since 0.0.1
 */
@Data
@Entity
@Table(name = "message_message_tracker")
public class MessageTracker {

    @Id
    private String trackerId;

    /**
     * 消息通道
     */
    private String channel;

    /**
     * 交换区
     */
    private String exchange;

    /**
     * 事件类型
     */
    private String region;

    /**
     * 发送序列
     */
    private Long sequence;

    @Consume("eventstore://create-tracker")
    public MessageTracker create(Event event) {
        MessageTracker tracker = new MessageTracker();
        tracker.setChannel(event.getChannel());
        tracker.setRegion(event.getEventName());
        tracker.setExchange(event.getExchangeName());
        tracker.setSequence(0L);
        tracker.setTrackerId(event.getExchangeName() + "-" + event.getChannel());
        return tracker;
    }

    @Consume("eventstore://tracker-move")
    public MessageTracker move(@Body MessageTracker tracker, @Header("eventMessages") List<EventMessage> eventMessages) {
        tracker.setSequence(eventMessages.get(eventMessages.size() - 1).getSequence());
        return tracker;
    }

    @Consume("publish://sending")
    public void send(@Body EventMessage eventMessage, @Header("template") RabbitTemplate template, @Header("tracker") MessageTracker tracker) {
        CorrelationData correlationData = new CorrelationData(JsonUtil.writeValueAsString(tracker));
        template.convertAndSend(tracker.getExchange(), tracker.getChannel(), eventMessage, correlationData);
    }
}
