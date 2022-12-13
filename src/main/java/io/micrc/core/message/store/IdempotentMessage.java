package io.micrc.core.message.store;

import io.micrc.core.persistence.snowflake.SnowFlakeIdentity;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 幂等消息
 *
 * @author hyosunghan
 * @date 2022/12/2 11:00
 * @since 0.0.1
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "message_idempotent_message")
public class IdempotentMessage {

    /**
     * 幂等消息ID
     */
    @Id
    private Long idempotentMessageId = SnowFlakeIdentity.getInstance().nextId();

    /**
     * 序列号
     */
    private Long sequence;

    /**
     * 发送方
     */
    private String sender;

    /**
     * 接收方
     */
    private String receiver;
}
