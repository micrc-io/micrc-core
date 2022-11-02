package io.micrc.core.message;

import com.rabbitmq.client.Channel;
import io.micrc.core.annotations.integration.message.MessageAdapter;
import io.micrc.core.message.store.EventMessage;
import io.micrc.core.rpc.Result;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.StringUtils;

import java.util.Map;

@Aspect
@Slf4j
@Configuration
public class MessageConsumeRouterExecution implements Ordered {

    @EndpointInject
    private ProducerTemplate template;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @Autowired
    private TransactionDefinition transactionDefinition;


    @Pointcut("@annotation(io.micrc.core.annotations.message.MessageExecution)")
    public void annotationPointCut() {/* leave it out */}

    @Around("annotationPointCut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Class<?>[] interfaces = proceedingJoinPoint.getTarget().getClass().getInterfaces();
        if (interfaces.length != 1) { // 实现类有且只能有一个接口
            throw new IllegalStateException(
                    "businesses service implementation class must only implement it's interface. ");
        }
        boolean custom = false;
        MessageAdapter annotation = interfaces[0].getAnnotation(MessageAdapter.class);
        if (annotation != null) {
            custom = annotation.custom();
        }
        Object[] args = proceedingJoinPoint.getArgs();
        EventMessage eventMessage = null;
        Channel channel = null;
        Message message = null;
        for (Object arg : args) {
            if(arg instanceof EventMessage){
                eventMessage = (EventMessage) arg;
            }
            if(arg instanceof Channel){
                channel = (Channel) arg;
            }
            if(arg instanceof Message){
                message = (Message) arg;
            }
        }
        if (null == eventMessage || null == channel || null == message) {
            return null;
        }
        Object retVal = null;
        // 事务处理器,手动开启事务
        TransactionStatus transactionStatus = platformTransactionManager.getTransaction(transactionDefinition);
        // 处理幂等 如果未消费,则
        Object object = JsonUtil.writeValueAsObject(message.getMessageProperties().getHeader("spring_returned_message_correlation").toString(), Object.class);
        Map<String, Object> messageDetail = ClassCastUtils.castHashMap(object, String.class, Object.class);
        Boolean consumed = null;
        try {
            consumed = template.requestBody("subscribe://idempotent-check", messageDetail, Boolean.class);
        } catch (IllegalStateException e) {
            log.info("the listener is init....");
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            platformTransactionManager.rollback(transactionStatus);
            return null;
        }
        // 转发调度
        if(consumed){
            // 如果是已重复消息 则先进行事务提交,然后进行ack应答
            platformTransactionManager.commit(transactionStatus);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
        if(!consumed){
            if (custom || annotation == null) {
                try {
                    Object obj = proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
                    platformTransactionManager.commit(transactionStatus);
                    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                    return obj;
                } catch (Throwable e) {
                    platformTransactionManager.rollback(transactionStatus);
                    channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
                    throw new RuntimeException(e);
                }
            }
            // 如果非已重复消息 转发至相应适配器
            Object resultObj = template.requestBody("message://" + messageDetail.get("region") + "Listener", eventMessage.getContent());
            Result<?> result = null;
            if(resultObj instanceof String){
                result = JsonUtil.writeValueAsObjectRetainNull((String) resultObj, Result.class);
            } else if(resultObj instanceof Result){
                result = (Result<?>) resultObj;
            } else {
                return null;
            }
            if(StringUtils.hasText(result.getCode()) && !"200".equals(result.getCode())){
                // 如果有异常 回滚事务 并应答失败进入死信
                platformTransactionManager.rollback(transactionStatus);
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
            }
            if("200".equals(result.getCode())) {
                // 如果执行正常则提交事务并应答成功
                platformTransactionManager.commit(transactionStatus);
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }
        }
        return retVal;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
