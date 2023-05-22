//package io.micrc.core.message.rabbit.store;
//
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//import org.apache.camel.Body;
//import org.apache.camel.Consume;
//import org.apache.camel.Header;
//
//import javax.persistence.Column;
//import javax.persistence.Entity;
//import javax.persistence.Id;
//import javax.persistence.Table;
//import java.io.Serializable;
//
///**
// * 事件消息
// *
// * @author tengwang
// * @date 2022/9/22 17:59
// * @since 0.0.1
// */
//@Data
//@Entity
//@NoArgsConstructor
//@AllArgsConstructor
//@Table(name = "rabbit_message_message_store")
//public class RabbitEventMessage implements Serializable {
//
//    /**
//     * 消息ID
//     */
//    @Id
//    private String messageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");
//
//    /**
//     * 消息创建时间
//     */
//    private Long createTime = System.currentTimeMillis();
//
//    @Column(columnDefinition = "LONGTEXT")
//    private String content;
//
//    private Long sequence;
//
//    private String region;
//
//    public static RabbitEventMessage store(String command, String event) {
//        RabbitEventMessage rabbitEventMessage = new RabbitEventMessage();
//        rabbitEventMessage.setContent(command);
//        rabbitEventMessage.setRegion(event);
//        return rabbitEventMessage;
//    }
//
//    public RabbitEventMessage(RabbitEventMessage rabbitEventMessage) {
//        this.messageId = rabbitEventMessage.getMessageId();
//        this.createTime = rabbitEventMessage.getCreateTime();
//        this.content = rabbitEventMessage.getContent();
//        this.sequence = rabbitEventMessage.getSequence();
//        this.region = rabbitEventMessage.getRegion();
//    }
//
//    @Consume("eventstore://message-set-content")
//    public RabbitEventMessage replaceContent(@Body RabbitEventMessage rabbitEventMessage, @Header("content") String content) {
//        RabbitEventMessage rabbitEventMessageNewInstance = new RabbitEventMessage(rabbitEventMessage);
//        rabbitEventMessageNewInstance.setContent(content);
//        return rabbitEventMessageNewInstance;
//    }
//}
