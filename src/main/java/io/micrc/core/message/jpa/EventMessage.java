package io.micrc.core.message.jpa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.camel.Body;
import org.apache.camel.Consume;
import org.apache.camel.Header;

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
    private String messageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");

    /**
     * 消息创建时间
     */
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

    @Consume("eventstore://message-set-content")
    public EventMessage replaceContent(@Body EventMessage eventMessage, @Header("content") String content) {
        eventMessage.setContent(content);
        return eventMessage;
    }
}
