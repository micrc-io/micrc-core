package io.micrc.core.message;

import io.micrc.core.annotations.message.MessageAdapter;
import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.rpc.Result;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

    private final ConcurrentHashMap<Long, Integer> map = new ConcurrentHashMap<>();

    @EndpointInject
    private ProducerTemplate template;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @Autowired
    private TransactionDefinition transactionDefinition;

    @Autowired
    private EventMessageRepository eventMessageRepository;

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
        MessageAdapter annotation = adapter.getAnnotation(MessageAdapter.class);
        String servicePath = annotation.commandServicePath();
        String[] servicePathSplit = servicePath.split("\\.");
        String serviceName = servicePathSplit[servicePathSplit.length - 1];
        boolean custom = annotation.custom();

        // 解析消息详情
        HashMap<String, String> messageDetail = new HashMap<>();
        messageDetail.put("serviceName", serviceName);
        transMessageHeaders(consumerRecord, messageDetail);

        // 死信用groupID过滤
        String messageGroupId = messageDetail.get("groupId");
        KafkaListener listenerAnnotation = getListenerAnnotation(proceedingJoinPoint);
        String listenerGroupId = listenerAnnotation.groupId();
        if (null != messageGroupId && !messageGroupId.isEmpty() && !messageGroupId.equals(listenerGroupId)) {
            // 无关死信
            acknowledgment.acknowledge();
            return null;
        }

        String mappingMapString = messageDetail.get("mappingMap");
        HashMap mappingMap = JsonUtil.writeValueAsObject(mappingMapString, HashMap.class);
        Object mappingObj = mappingMap.get(serviceName);
        HashMap mapping = JsonUtil.writeObjectAsObject(mappingObj, HashMap.class);
        String mappingString = mapping == null ? null : (String) mapping.get("mappingPath");

        String listenerEvent = annotation.eventName();
        String messageEvent = messageDetail.get("event");
        if (null == mappingString || !listenerEvent.equals(messageEvent)) {
            // 不需要消费
            acknowledgment.acknowledge();
            return null;
        }
        Object content = consumerRecord.value();
        String contentString = JsonUtil.transform(mappingString, content);
        messageDetail.put("content", contentString);

        // 事务处理器,手动开启事务
        TransactionStatus transactionStatus = platformTransactionManager.getTransaction(transactionDefinition);
        // 幂等检查
        Boolean consumed = null;
        try {
            consumed = template.requestBody("subscribe://idempotent-check", messageDetail, Boolean.class);
        } catch (IllegalStateException e) {
            platformTransactionManager.rollback(transactionStatus);
            // 稍后5秒消费
            acknowledgment.nack(Duration.ofMillis(5 * 1000));
            return null;
        }
        log.info("接到消息：" + messageDetail.get("messageId") + "，当前组" + listenerGroupId + "，来自死信" + (null != messageGroupId));
        // 转发调度
        if(consumed){
            // 如果是已重复消息 则先进行事务提交,然后进行ack应答
            platformTransactionManager.commit(transactionStatus);
            acknowledgment.acknowledge();
            log.warn("接收失败: 重复消费" + messageDetail.get("messageId"));
            return null;
        }

        String batchModel = (String) mapping.get("batchModel");
        if (batchModel != null && !batchModel.isEmpty()) {
            // 批量事件需要拆分重发
            String batchModelPath = "/" + batchModel;
            Object eventDataList = JsonUtil.readPath(contentString, batchModelPath);
            if (eventDataList instanceof List) {
                ((List) eventDataList).forEach(eventData -> {
                    EventMessage eventMessage = new EventMessage();
                    String splitContentString = JsonUtil.patch(contentString, batchModelPath, JsonUtil.writeValueAsString(eventData));
                    eventMessage.setContent(splitContentString);
                    eventMessage.setOriginalTopic(listenerAnnotation.topics()[0]);
                    eventMessage.setOriginalMapping(JsonUtil.writeValueAsString(mappingObj));
                    eventMessage.setRegion(messageEvent);
                    eventMessage.setStatus("WAITING");
                    eventMessageRepository.save(eventMessage);
                });
                platformTransactionManager.commit(transactionStatus);
                acknowledgment.acknowledge();
                return null;
            }
        }

        if (custom) {
            Object obj = proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
            platformTransactionManager.commit(transactionStatus);
            acknowledgment.acknowledge();
            log.info("接收成功: " + messageDetail.get("messageId"));
            return obj;
        }

        // 如果非已重复消息 转发至相应适配器
        Object resultObj = template.requestBody("message://" + adapterName, messageDetail.get("content"));
        Result<?> result = new Result<>();
        if(resultObj instanceof String){
            result = JsonUtil.writeValueAsObjectRetainNull((String) resultObj, Result.class);
        } else if(resultObj instanceof Result){
            result = (Result<?>) resultObj;
        }
        if(StringUtils.hasText(result.getCode()) && !"200".equals(result.getCode())){
            // 如果有异常 回滚事务 并应答失败进入死信
            platformTransactionManager.rollback(transactionStatus);
            log.error("接收失败: " + messageDetail.get("messageId"));
            throw new IllegalStateException("sys execute error");
        } else {
            // 如果执行正常则提交事务并应答成功
            platformTransactionManager.commit(transactionStatus);
            acknowledgment.acknowledge();
            log.info("接收成功: " + messageDetail.get("messageId"));
            return null;
        }
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
