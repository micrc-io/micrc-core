package io.micrc.core.message;

import io.micrc.core.message.store.EventMessage;
import io.micrc.core.message.store.EventMessageRepository;
import io.micrc.core.message.store.IdempotentMessage;
import io.micrc.core.message.store.IdempotentMessageRepository;
import io.micrc.lib.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class MessageConsumeExecutor {

    @EndpointInject
    private ProducerTemplate template;

    @Autowired
    private EventMessageRepository eventMessageRepository;

    @Autowired
    private IdempotentMessageRepository idempotentMessageRepository;

    @Transactional(rollbackFor = Exception.class)
    public boolean preExecute(HashMap<String, String> messageDetail) {
        Long messageId = Long.valueOf(messageDetail.get("messageId"));
        String serviceName = messageDetail.get("serviceName");
        IdempotentMessage idempotentMessage = idempotentMessageRepository.findFirstBySequenceAndReceiver(messageId, serviceName);
        if (idempotentMessage == null) {
            idempotentMessage = new IdempotentMessage();
            idempotentMessage.setSender(messageDetail.get("senderHost"));
            idempotentMessage.setSequence(messageId);
            idempotentMessage.setReceiver(serviceName);
            idempotentMessage.setStatus("RECEIVING");
            idempotentMessageRepository.save(idempotentMessage);
        }
        return received(idempotentMessage, serviceName, messageId.toString());
    }

    private static boolean received(IdempotentMessage idempotentMessage, String serviceName, String messageId) {
        boolean received = "RECEIVED".equals(idempotentMessage.getStatus());
        if (received) {
            log.warn("接收重复{}: 消息{}", serviceName, messageId);
        }
        return received;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object execute(HashMap<String, String> messageDetail, ProceedingJoinPoint proceedingJoinPoint, boolean custom, HashMap mapping) throws Throwable {
        String eventName = messageDetail.get("event");
        String messageGroupId = messageDetail.get("groupId");
        String messageId = messageDetail.get("messageId");
        String topicName = messageDetail.get("topicName");
        String adapterName = messageDetail.get("adapterName");
        String serviceName = messageDetail.get("serviceName");
        String targetContent = messageDetail.get("content");
        try {
            log.info("接收开始{}: 消息{}，参数{}，死信{}", serviceName, messageId, targetContent, null != messageGroupId);
            Object executeResult = null;
            IdempotentMessage idempotentMessage = idempotentMessageRepository.findFirstBySequenceAndReceiver(Long.valueOf(messageId), serviceName);
            if (received(idempotentMessage, serviceName, messageId)) {
                return null;
            }
            idempotentMessage.setStatus("RECEIVED");
            idempotentMessageRepository.save(idempotentMessage);
            String batchModel = (String) mapping.get("batchModel");
            String batchModelPath = "/" + batchModel;
            if (batchModel != null && !batchModel.isEmpty() && JsonUtil.readPath(targetContent, batchModelPath) instanceof List) {
                copyEvent(mapping, topicName, targetContent, batchModelPath, eventName);
            } else if (custom) {
                executeResult = proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
            } else {
                template.requestBody("message://" + adapterName + "-" + eventName + "-" + serviceName, targetContent);
            }
            log.info("接收成功{}: 消息{}", serviceName, messageId);
            return executeResult;
        } catch (Throwable e) {
            log.error("接收失败{}: 消息{}, 错误信息{}", serviceName, messageId, e.getLocalizedMessage());
            throw e;
        }
    }

    private void copyEvent(HashMap mapping, String topicName, String targetContent, String batchModelPath, String eventName) {
        mapping.put("mappingPath", ".");
        mapping.put("batchModel", null);
        ((List) JsonUtil.readPath(targetContent, batchModelPath)).forEach(eventData -> {
            EventMessage eventMessage = new EventMessage();
            String splitContentString = JsonUtil.patch(targetContent, batchModelPath, JsonUtil.writeValueAsString(eventData));
            eventMessage.setContent(splitContentString);
            eventMessage.setOriginalTopic(topicName);
            eventMessage.setOriginalMapping(JsonUtil.writeValueAsString(mapping));
            eventMessage.setRegion(eventName);
            eventMessage.setStatus("WAITING");
            eventMessageRepository.save(eventMessage);
        });
    }
}
