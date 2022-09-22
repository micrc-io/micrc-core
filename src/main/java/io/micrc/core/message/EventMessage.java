package io.micrc.core.message;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 事件消息
 *
 * @author tengwang
 * @date 2022/9/22 17:59
 * @since 0.0.1
 */
@Data
@Entity
@Table(name = "message_message_store")
public class EventMessage {

    @Id
    private String messageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");

    private Long createTime = System.currentTimeMillis();

    private String content;

    private Long sequence;

    private String region;

    public EventMessage store(String command, String event) {
        EventMessage eventMessage = new EventMessage();
        eventMessage.setContent(command);
        eventMessage.setRegion(event);
        return eventMessage;
    }
}
