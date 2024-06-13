package io.micrc.core.message;

import io.micrc.core.annotations.message.Adapter;
import io.micrc.core.annotations.message.MessageAdapter;
import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.message.store.IdempotentMessage;
import io.micrc.core.message.store.IdempotentMessageRepository;
import io.micrc.lib.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import java.lang.reflect.Method;
import java.util.*;
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

    @EndpointInject
    private ProducerTemplate template;

    @Autowired
    private EventMessageRepository eventMessageRepository;

    @Autowired
    private IdempotentMessageRepository idempotentMessageRepository;

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

        // 解析监听器注解参数
        Class<?>[] interfaces = proceedingJoinPoint.getTarget().getClass().getInterfaces();
        if (interfaces.length != 1) { // 实现类有且只能有一个接口
            throw new IllegalStateException(
                    "businesses service implementation class must only implement it's interface. ");
        }
        Class<?> adapter = interfaces[0];
        String adapterName = adapter.getSimpleName();
        MessageAdapter messageAdapter = adapter.getAnnotation(MessageAdapter.class);

        // 解析消息详情
        HashMap<String, String> messageDetail = new HashMap<>();
        transMessageHeaders(consumerRecord, messageDetail);
        String eventName = messageDetail.get("event");
        String messageGroupId = messageDetail.get("groupId");
        String mappingMapString = messageDetail.get("mappingMap");
        String messageId = messageDetail.get("messageId");
        String senderHost = messageDetail.get("senderHost");

        Adapter[]  adapters = messageAdapter.value();
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
        messageDetail.put("serviceName", serviceName);

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
        if (null != messageGroupId && !messageGroupId.isEmpty() && !messageGroupId.equals(listenerGroupId)) {
            // 发给其他指定组的无关死信
            acknowledgment.acknowledge();
            return null;
        }

        Object sourceContent = consumerRecord.value();
        String targetContent = JsonUtil.transform(mappingString, sourceContent);
        messageDetail.put("content", targetContent);
        log.info("接收开始{}: 消息{}，参数{}，死信{}", serviceName, messageId, targetContent, null != messageGroupId);
        try {
            Object executeResult = null;
            IdempotentMessage idempotentMessage = new IdempotentMessage();
            idempotentMessage.setSender(senderHost);
            idempotentMessage.setSequence(Long.valueOf(messageId));
            idempotentMessage.setReceiver(serviceName);
            idempotentMessageRepository.save(idempotentMessage);
            String batchModel = (String) mapping.get("batchModel");
            String batchModelPath = "/" + batchModel;
            if (batchModel != null && !batchModel.isEmpty() && JsonUtil.readPath(targetContent, batchModelPath) instanceof List) {
                copyEvent(mapping, consumerRecord, targetContent, batchModelPath, eventName);
            } else if (messageAdapter.custom()) {
                executeResult = proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
            } else {
                template.requestBody("message://" + adapterName + "-" + eventName + "-" + serviceName, targetContent);
            }
            log.info("接收成功{}: 消息{}", serviceName, messageId);
            return executeResult;
        } catch (Throwable e) {
            if (e instanceof DataIntegrityViolationException && e.getCause() instanceof ConstraintViolationException
                    && ((ConstraintViolationException) e.getCause()).getSQLException().getErrorCode() == 1062) {
                log.warn("接收重复{}: 消息{}", serviceName, messageId);
                return null;
            } else {
                log.error("接收失败{}: 消息{}, 错误信息{}", serviceName, messageId, e.getLocalizedMessage());
                throw e;
            }
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private void copyEvent(HashMap mapping, ConsumerRecord<?, ?> consumerRecord, String targetContent, String batchModelPath, String eventName) {
        mapping.put("mappingPath", ".");
        mapping.put("batchModel", null);
        ConsumerRecord<?, ?> finalConsumerRecord = consumerRecord;
        ((List) JsonUtil.readPath(targetContent, batchModelPath)).forEach(eventData -> {
            EventMessage eventMessage = new EventMessage();
            String splitContentString = JsonUtil.patch(targetContent, batchModelPath, JsonUtil.writeValueAsString(eventData));
            eventMessage.setContent(splitContentString);
            eventMessage.setOriginalTopic(finalConsumerRecord.topic());
            eventMessage.setOriginalMapping(JsonUtil.writeValueAsString(mapping));
            eventMessage.setRegion(eventName);
            eventMessage.setStatus("WAITING");
            eventMessageRepository.save(eventMessage);
        });
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
