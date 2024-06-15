package io.micrc.core.message;

import io.micrc.core.annotations.message.Adapter;
import io.micrc.core.annotations.message.MessageAdapter;
import io.micrc.lib.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;
/**
 * 消息消费路由执行
 *
 * @author hyosunghan
 * @date 2022/12/01 14:39
 * @since 0.0.1
 */
@Aspect
@Slf4j
@Configuration
public class MessageConsumeRouterExecution implements Ordered {

    @Autowired
    private MessageConsumeExecutor messageConsumeExecutor;

    @Pointcut("@annotation(io.micrc.core.annotations.message.MessageExecution)")
    public void annotationPointCut() {/* leave it out */}

    @Around("annotationPointCut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        // 解析消息入参
        Object[] args = proceedingJoinPoint.getArgs();
        ConsumerRecord<?, ?> consumerRecord = null;
        Acknowledgment acknowledgment = null;
        for (Object arg : args) {
            if(arg instanceof ConsumerRecord){
                consumerRecord = (ConsumerRecord<?, ?>) arg;
            }
            if(arg instanceof Acknowledgment){
                acknowledgment = (Acknowledgment) arg;
            }
        }
        if (null == consumerRecord || null == acknowledgment) {
            throw new IllegalArgumentException("sys args error");
        }
        HashMap<String, String> messageDetail = new HashMap<>();
        transMessageHeaders(consumerRecord, messageDetail);

        // 解析监听器注解参数
        Class<?>[] interfaces = proceedingJoinPoint.getTarget().getClass().getInterfaces();
        if (interfaces.length != 1) { // 实现类有且只能有一个接口
            throw new IllegalStateException(
                    "businesses service implementation class must only implement it's interface. ");
        }
        Class<?> adapter = interfaces[0];
        MessageAdapter messageAdapter = adapter.getAnnotation(MessageAdapter.class);
        Adapter[]  adapters = messageAdapter.value();
        String eventName = messageDetail.get("event");
        Optional<Adapter> optionalAnnotation = Arrays.stream(adapters).filter(a -> a.eventName().equals(eventName)).findFirst();
        Adapter annotation = optionalAnnotation.orElse(null);
        if (null == annotation) {
            // 接收方未指定执行逻辑
            acknowledgment.acknowledge();
            return null;
        }
        String servicePath = annotation.commandServicePath();
        String[] servicePathSplit = servicePath.split("\\.");
        String serviceName = servicePathSplit[servicePathSplit.length - 1];

        String mappingMapString = messageDetail.get("mappingMap");
        HashMap mappingMap = JsonUtil.writeValueAsObject(mappingMapString, HashMap.class);
        Object mappingObj = mappingMap.get(serviceName);
        HashMap mapping = JsonUtil.writeObjectAsObject(mappingObj, HashMap.class);
        String mappingString = mapping == null ? null : (String) mapping.get("mappingPath");
        if (null == mappingString) {
            // 发送方未指定消息映射
            acknowledgment.acknowledge();
            return null;
        }

        KafkaListener listenerAnnotation = getListenerAnnotation(proceedingJoinPoint);
        String listenerGroupId = listenerAnnotation.groupId();
        String messageGroupId = messageDetail.get("groupId");
        if (null != messageGroupId && !messageGroupId.isEmpty() && !messageGroupId.equals(listenerGroupId)) {
            // 发给其他指定组的无关死信
            acknowledgment.acknowledge();
            return null;
        }

        messageDetail.put("topicName", consumerRecord.topic());
        messageDetail.put("adapterName", adapter.getSimpleName());
        messageDetail.put("serviceName", serviceName);
        messageDetail.put("content", JsonUtil.transform(mappingString, consumerRecord.value()));
        Object result = messageConsumeExecutor.execute(messageDetail, proceedingJoinPoint, messageAdapter.custom(), mapping);
        acknowledgment.acknowledge();
        return result;
    }

    private static KafkaListener getListenerAnnotation(ProceedingJoinPoint proceedingJoinPoint) throws NoSuchMethodException {
        Class<?> targetClass = proceedingJoinPoint.getTarget().getClass();
        Signature signature = proceedingJoinPoint.getSignature();
        MethodSignature ms = (MethodSignature) signature;
        Method method = targetClass.getDeclaredMethod(ms.getName(), ms.getParameterTypes());
        return method.getAnnotation(KafkaListener.class);
    }

    private void transMessageHeaders(ConsumerRecord<?, ?> consumerRecord, HashMap<String, String> messageDetail) {
        Iterator<Header> headerIterator = consumerRecord.headers().iterator();
        while (headerIterator.hasNext()) {
            Header header = headerIterator.next();
            messageDetail.put(header.key(), new String(header.value()));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
