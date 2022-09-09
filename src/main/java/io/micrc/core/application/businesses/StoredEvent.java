package io.micrc.core.application.businesses;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.UUID;

/**
 * 消息模型
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/1/14 2:34 PM
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "message_message_store", indexes = {
        @Index(columnList = "messageId"),
        @Index(columnList = "createTime"),
        @Index(columnList = "region"),
        @Index(columnList = "sequence"),
        @Index(columnList = "sequence, region")
})
// @TypeDef(name = "json", typeClass = JsonType.class)
public class StoredEvent {

    @Id
    private String messageId = System.currentTimeMillis() + UUID.randomUUID().toString();

    @Column(nullable = false)
    private Long createTime = System.currentTimeMillis();

    @Type(type = "json")
    @Column(columnDefinition = "json")
    private Command command;

    @Column(nullable = false, insertable = false, updatable = false, columnDefinition = "BIGINT AUTO_INCREMENT UNIQUE")
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long sequence;

    /**
     * 事件类型
     */
    @Column(nullable = false)
    private String region;

    public void sendMessage(Command command, String event) {
        this.command = command;
        this.region = event;
    }
}
