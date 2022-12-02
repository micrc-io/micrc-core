package io.micrc.core.message.tracking;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 消息跟踪器
 *
 * @author hyosunghan
 * @date 2022/12/01 14:39
 * @since 0.0.1
 */
@Data
@Entity
@Table(name = "message_message_tracker")
public class MessageTracker {

    @Id
    private String trackerId;

    /**
     * 主题名称
     */
    private String topicName;

    /**
     * 发送方名称
     */
    private String senderName;

    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 发送序列
     */
    private Long sequence;
}
