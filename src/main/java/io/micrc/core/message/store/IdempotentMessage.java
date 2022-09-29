package io.micrc.core.message.store;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Consume;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Map;

/**
 * 幂等消息
 *
 * @author tengwang
 * @date 2022/9/29 11:00
 * @since 0.0.1
 */
@Slf4j
@Data
@Entity
@NoArgsConstructor
@Table(name = "message_idempotent_message")
public class IdempotentMessage {

    /**
     * 幂等消息ID
     */
    @Id
    private String idempotentMessageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");

    /**
     * 序列号
     */
    private Long sequence;

    /**
     * 发送通道
     */
    private String channel;

    /**
     * 发送交换区
     */
    private String exchange;

    /**
     * 消息类型
     */
    private String region;

    @Consume("subscribe://idempotent-message")
    public IdempotentMessage idempotent(Map<String, Object> messageDetail) {
        IdempotentMessage idempotent = new IdempotentMessage();
        idempotent.setChannel((String) messageDetail.get("channel"));
        idempotent.setSequence(Long.valueOf(messageDetail.get("sequence").toString()));
        idempotent.setExchange((String) messageDetail.get("exchange"));
        idempotent.setRegion((String) messageDetail.get("region"));
        return idempotent;
    }
}
