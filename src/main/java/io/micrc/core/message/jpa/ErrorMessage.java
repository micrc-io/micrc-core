package io.micrc.core.message.jpa;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 异常消息-包含发送失败与消费失败消息
 *
 * @author tengwang
 * @date 2022/9/26 10:22
 * @since 0.0.1
 */
@Data
@Entity
@Table(name = "message_error_message")
public class ErrorMessage {

    /**
     * 失败消息ID
     */
    @Id
    private String errorMessageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");

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

    /**
     * 失败原因 send error reason - the SEND is in send step error, the DEAD_MESSAGE is on consumer can not consumer error
     */
    private String reason;

    /**
     * 最后失败时间
     */
    private Long lastErrorTime = System.currentTimeMillis();

    /**
     * 失败次数
     */
    private Integer errorFrequency = 1;

    public ErrorMessage haveError(String region, Long sequence, String exchange, String channel, String reason) {
        ErrorMessage errorMessage = new ErrorMessage();
        errorMessage.setRegion(region);
        errorMessage.setSequence(sequence);
        errorMessage.setExchange(exchange);
        errorMessage.setChannel(channel);
        errorMessage.setReason(reason);
        return errorMessage;
    }
}