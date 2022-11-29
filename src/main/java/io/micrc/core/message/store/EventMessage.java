package io.micrc.core.message.store;

import io.micrc.core.persistence.snowflake.SnowFlakeIdentity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * 事件消息
 *
 * @author tengwang
 * @date 2022/9/22 17:59
 * @since 0.0.1
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_message_store")
public class EventMessage implements Serializable {

    /**
     * 消息ID
     */
    @Id
    private Long messageId = SnowFlakeIdentity.getInstance().nextId();

    /**
     * 消息创建时间
     */
    private Long createTime = System.currentTimeMillis();
    
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    private String region;

    public static EventMessage store(String command, String event) {
        EventMessage eventMessage = new EventMessage();
        eventMessage.setContent(command);
        eventMessage.setRegion(event);
        return eventMessage;
    }

    public EventMessage(EventMessage eventMessage) {
        this.messageId = eventMessage.getMessageId();
        this.createTime = eventMessage.getCreateTime();
        this.content = eventMessage.getContent();
        this.region = eventMessage.getRegion();
    }
}
