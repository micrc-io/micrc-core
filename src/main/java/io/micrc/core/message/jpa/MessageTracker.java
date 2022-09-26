package io.micrc.core.message.jpa;

import io.micrc.core.message.MessageRouteConfiguration.EventsInfo.Event;
import lombok.Data;
import org.apache.camel.Consume;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

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
     * 交换区 ? 必要吗?
     */
    private String exchange;

    /**
     * 事件类型
     */
    private String region;

    /**
     * 发送序列
     */
    private Integer sequence;

    @Consume("eventstore://create-tracker")
    public MessageTracker create(Event event) {
        MessageTracker tracker = new MessageTracker();
        tracker.setChannel(event.getChannel());
        tracker.setRegion(event.getEventName());
        tracker.setExchange(event.getExchangeName());
        tracker.setSequence(0);
        tracker.setTrackerId(event.getExchangeName() + "-" + event.getChannel());
        return tracker;
    }

    @Consume("eventstore://tracker-move")
    //TODO tengwang 这里不会端点的入参方法 学会后修正写法
    public MessageTracker moveSequence(MessageTracker tracker, Integer sequence) {
        tracker.setSequence(sequence + tracker.getSequence());
        return tracker;
    }
}
