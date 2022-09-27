package io.micrc.core.message;

import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate.ConfirmCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate.ReturnsCallback;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author tengwang
 * @date 2022/9/27 14:04
 * @since 0.0.1
 */

@Component
public class MessageCallback implements ConfirmCallback, ReturnsCallback {

    /**
     * 成功失败都会回调(ack判断)
     *
     * @param correlationData correlation data for the callback.
     * @param ack             true for ack, false for nack
     * @param cause           An optional cause, for nack, when available, otherwise null.
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        System.err.println("correlationData: " + correlationData);
        if (!ack) {
            // 失败回写异常消息表
        }
    }

    /**
     * 消息未从路由成功发送到队列的处理方案
     *
     * @param returned the returned message and metadata.
     */
    @Override
    public void returnedMessage(ReturnedMessage returned) {
        System.out.println(returned);
    }
}
