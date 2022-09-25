package io.micrc.core.message;

import lombok.Data;
import org.apache.camel.Consume;

/**
 * 消息跟踪器
 *
 * @author tengwang
 * @date 2022/9/23 14:39
 * @since 0.0.1
 */
@Data
public class MessageTracker {

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
    public MessageTracker create(String channel) {
        MessageTracker tracker = new MessageTracker();
        tracker.setChannel(channel);
        tracker.setRegion(channel.split("-")[0]);
        tracker.setSequence(0);
        return tracker;
    }

    @Consume("eventstore://tracker-move")
    //TODO tengwang 这里不会端点的入参方法 学会后修正写法
    public MessageTracker moveSequence(MessageTracker tracker, Integer sequence) {
        tracker.setSequence(sequence + tracker.getSequence());
        return tracker;
    }
}
