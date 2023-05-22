//package io.micrc.core.message.rabbit.tracking;
//
//import io.micrc.core.message.rabbit.store.RabbitEventMessage;
//import io.micrc.lib.ClassCastUtils;
//import io.micrc.lib.JsonUtil;
//import lombok.SneakyThrows;
//import org.apache.camel.EndpointInject;
//import org.apache.camel.ProducerTemplate;
//import org.springframework.amqp.core.ReturnedMessage;
//import org.springframework.amqp.rabbit.connection.CorrelationData;
//import org.springframework.amqp.rabbit.core.RabbitTemplate.ConfirmCallback;
//import org.springframework.amqp.rabbit.core.RabbitTemplate.ReturnsCallback;
//import org.springframework.stereotype.Component;
//
//import java.io.ByteArrayInputStream;
//import java.io.ObjectInputStream;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * 消息回调处理
// *
// * @author tengwang
// * @date 2022/9/27 14:04
// * @since 0.0.1
// */
//@Component
//public class RabbitMessageCallback implements ConfirmCallback, ReturnsCallback {
//
//    @EndpointInject
//    private ProducerTemplate template;
//
//
//    /**
//     * 成功失败都会回调(ack判断)
//     *
//     * @param correlationData correlation data for the callback.
//     * @param ack             true for ack, false for nack
//     * @param cause           An optional cause, for nack, when available, otherwise null.
//     */
//    @Override
//    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
//        Object object = JsonUtil.writeValueAsObject(correlationData.getId(), Object.class);
//        Map<String, Object> messageDetail = ClassCastUtils.castHashMap(object, String.class, Object.class);
//        String messageType = (String) messageDetail.get("type");
//        // FixME tengwang ack成功不可作为删除异常表数据的前提条件
////        if(ack){
////            // 异常消息且成功--去删除异常表里的死信消息
////            // 正常消息且成功--不予处理
////            template.sendBodyAndHeader("publish://success-sending-resolve", messageDetail, "type", messageType);
////        }
//        if(!ack){
//            // 异常消息且失败--修改异常表,记录发送次数
//            // 正常消息且失败--添加异常消息并记录原因为发送失败
//            Map<String, Object> headers = new HashMap<>();
//            RabbitEventMessage rabbitEventMessage = null;
//            if(null != correlationData.getReturned()){
//                // 当交换区不存在的时候,消息体为空
//                rabbitEventMessage = (RabbitEventMessage) toObject(correlationData.getReturned().getMessage().getBody());
//            }
//            headers.put("eventMessage", rabbitEventMessage);
//            headers.put("type", messageType);
//            template.sendBodyAndHeaders("publish://error-sending-resolve", messageDetail, headers);
//        }
//    }
//
//    /**
//     * 消息未从路由成功发送到队列的处理方案
//     *
//     * @param returned the returned message and metadata.
//     */
//    @Override
//    public void returnedMessage(ReturnedMessage returned) {
//        Object object = JsonUtil.writeValueAsObject(returned.getMessage().getMessageProperties().getHeader("spring_returned_message_correlation").toString(), Object.class);
//        Map<String, Object> messageDetail = ClassCastUtils.castHashMap(object, String.class, Object.class);
//        String messageType = (String) messageDetail.get("type");
//        RabbitEventMessage rabbitEventMessage = (RabbitEventMessage) toObject(returned.getMessage().getBody());
//        Map<String, Object> headers = new HashMap<>();
//        headers.put("eventMessage", rabbitEventMessage);
//        headers.put("type", messageType);
//        template.sendBodyAndHeaders("publish://error-return-resolve", messageDetail, headers);
//    }
//
//    @SneakyThrows
//    public Object toObject(byte[] bytes) {
//        Object obj = null;
//        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
//        ObjectInputStream ois = new ObjectInputStream(bis);
//        obj = ois.readObject();
//        ois.close();
//        bis.close();
//        return obj;
//    }
//}
