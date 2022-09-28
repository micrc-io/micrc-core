package io.micrc.core.message.jpa;

import com.rabbitmq.client.Channel;
import io.micrc.core.framework.json.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.*;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.HashMap;
import java.util.Map;

/**
 * 异常消息-包含发送失败与消费失败消息
 *
 * @author tengwang
 * @date 2022/9/26 10:22
 * @since 0.0.1
 */
@Slf4j
@Data
@Entity
@NoArgsConstructor
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
    private Integer errorFrequency = 0;

    /**
     * 发送状态 NOT_SEND未发送 STOP不发送 SENDING发送中
     */
    private String state = "NOT_SEND";

    /**
     * 发送内容
     */
    private String content;

    @SuppressWarnings("unchecked")
    public ErrorMessage(EventMessage eventMessage, Map<String, Object> eventDetail, String reason, String state) {
        this.sequence = Long.valueOf(eventDetail.get("sequence").toString());
        this.channel = (String) eventDetail.get("channel");
        this.exchange = (String) eventDetail.get("exchange");
        this.region = (String) eventDetail.get("region");
        this.reason = reason;
        this.errorFrequency = this.errorFrequency + 1;
        this.state = state;
        this.content = eventMessage.getContent();
    }

    @Consume("eventstore://error-message-sending")
    public ErrorMessage sending(@Body ErrorMessage errorMessage) {
        errorMessage.setState("SENDING");
        return errorMessage;
    }

    @Consume("eventstore://dead-message-store")
    public ErrorMessage deadMessageStore(@Body EventMessage eventMessage, @Header("eventDetail") String eventDetailJson) {
        Map<String, Object> eventDetail = JsonUtil.writeValueAsObject(eventDetailJson, HashMap.class);
        ErrorMessage errorMessage = new ErrorMessage(eventMessage, eventDetail, "DEAD_MESSAGE", "STOP");
        return errorMessage;
    }

    @Consume("eventstore://send-error-error-message-store")
    public ErrorMessage sendErrorMessageStore(@Body ErrorMessage errorMessage) {
        errorMessage.setErrorFrequency(errorMessage.getErrorFrequency() + 1);
        errorMessage.setLastErrorTime(System.currentTimeMillis());
        errorMessage.setReason("SEND");
        errorMessage.setState("NOT_SEND");
        return errorMessage;
    }

    @Consume("eventstore://send-normal-error-message-store")
    public ErrorMessage errorNormalMessageStore(@Body Map<String, Object> eventDetail, @Header("eventMessage") EventMessage eventMessage) {
        ErrorMessage errorMessage = new ErrorMessage(eventMessage, eventDetail, "SEND", "NOT_SEND");
        return errorMessage;
    }

    @Component("DeadMessageResolver")
    public static class DeadMessageResolver {

        @EndpointInject("subscribe://dead-message")
        private ProducerTemplate template;

        @SneakyThrows
//        @RabbitListener(
//                bindings = @QueueBinding(
//                        value = @Queue(value = MessageAutoConfiguration.DEAD_LETTER_QUEUE_NAME),
//                        exchange = @Exchange(value = MessageAutoConfiguration.DEAD_LETTER_EXCHANGE_NAME),
//                        key = MessageAutoConfiguration.DEAD_LETTER_ROUTING_KEY
//                )
//        )
        public void adapt(EventMessage eventMessage, Channel channel, Message message) {
            log.info("落盘死信消息-{}", message);
            template.sendBodyAndHeader(eventMessage, "eventDetail", message.getMessageProperties().getHeader("spring_returned_message_correlation"));
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }

}