package io.micrc.core.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import io.micrc.core.annotations.message.MessageAdapter;
import io.micrc.core.rpc.Result;
import io.micrc.lib.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
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
            return null;
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
        if (annotation == null) {
            acknowledgment.nack(Duration.ofMillis(0));
            return null;
        }
        String servicePath = annotation.commandServicePath();
        String[] servicePathSplit = servicePath.split("\\.");
        String serviceName = servicePathSplit[servicePathSplit.length - 1];
        String eventName = annotation.eventName();
        boolean custom = annotation.custom();

        // 解析消息详情
        HashMap<String, Object> messageDetail = new HashMap<>();
        messageDetail.put("servicePath", servicePath);
        transMessageHeaders(consumerRecord, serviceName, messageDetail);
        Object mappingString = messageDetail.get("mappingPath");
        if (null == mappingString || null == eventName || !eventName.equals(messageDetail.get("event"))) {
            acknowledgment.nack(Duration.ofMillis(0));
            return null;
        }
        Object content = consumerRecord.value();
        Expression expression = Parser.compileString((String) mappingString);
        JsonNode resultNode = expression.apply(JsonUtil.readTree(content));
        messageDetail.put("content", JsonUtil.writeValueAsStringRetainNull(resultNode));

        // 事务处理器,手动开启事务
        TransactionStatus transactionStatus = platformTransactionManager.getTransaction(transactionDefinition);
        // 幂等检查
        Boolean consumed = null;
        try {
            consumed = template.requestBody("subscribe://idempotent-check", messageDetail, Boolean.class);
        } catch (IllegalStateException e) {
            log.info("the listener is init....");
            platformTransactionManager.rollback(transactionStatus);
            acknowledgment.nack(Duration.ofMillis(5 * 1000));
            return null;
        }
        log.info("消息已接收: 重复{}, 内容{}", consumed, JsonUtil.writeValueAsString(messageDetail));
        // 转发调度
        if(consumed){
            // 如果是已重复消息 则先进行事务提交,然后进行ack应答
            platformTransactionManager.commit(transactionStatus);
            acknowledgment.acknowledge();
            return null;
        }
        if (custom) {
            try {
                Object obj = proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
                platformTransactionManager.commit(transactionStatus);
                acknowledgment.acknowledge();
                return obj;
            } catch (Throwable e) {
                platformTransactionManager.rollback(transactionStatus);
                acknowledgment.nack(Duration.ofMillis(0));
                throw new RuntimeException(e);
            }
        }

        // 如果非已重复消息 转发至相应适配器
        Object resultObj = template.requestBody("message://" + adapterName, messageDetail.get("content"));
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
            acknowledgment.nack(Duration.ofMillis(0));
        } else {
            // 如果执行正常则提交事务并应答成功
            platformTransactionManager.commit(transactionStatus);
            acknowledgment.acknowledge();
        }
        return null;
    }

    private void transMessageHeaders(ConsumerRecord<?, ?> consumerRecord, String serviceName, HashMap<String, Object> messageDetail) {
        Iterator<Header> headerIterator = consumerRecord.headers().iterator();
        StringBuilder headerString = new StringBuilder();
        while (headerIterator.hasNext()) {
            Header header = headerIterator.next();
            String k = header.key();
            String v = new String(header.value());
            headerString.append(",").append(k).append("=").append(v);
            if ("sequence".equals(k)) {
                messageDetail.put(k, Long.valueOf(v));
            } else if ("sender".equals(k) || "event".equals(k)) {
                messageDetail.put(k, v);
            } else if ("mappingMap".equals(k)) {
                HashMap mappingMap = JsonUtil.writeValueAsObject(v, HashMap.class);
                Object mappingObj = mappingMap.get(serviceName);
                HashMap mapping = JsonUtil.writeObjectAsObject(mappingObj, HashMap.class);
                messageDetail.put("mappingPath", mapping.get("mappingPath"));
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
