package io.micrc.core.message.springboot;

import io.micrc.core.message.MessageConsumeRouterExecution;
import io.micrc.core.message.MessageRouteConfiguration;
import io.micrc.core.message.store.MessagePublisherSchedule;
import io.micrc.core.message.error.ErrorMessage;
import io.micrc.lib.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;


/**
 * message auto configuration. 注册publish/subscribe组件
 *
 * @author weiguan
 * @date 2022-09-06 19:29
 * @since 0.0.1
 */
@Slf4j
@AutoConfigureAfter(CamelAutoConfiguration.class)
@Configuration
@EnableKafka
@Import({
        MessageRouteConfiguration.class,
        MessagePublisherSchedule.class,
        MessageConsumeRouterExecution.class
})
@EntityScan(basePackages = {"io.micrc.core.message.store", "io.micrc.core.message.error"})
@EnableJpaRepositories(basePackages = {"io.micrc.core.message.store", "io.micrc.core.message.error"})
public class MessageAutoConfiguration {

    @Autowired
    Environment environment;

    @EndpointInject
    private ProducerTemplate producerTemplate;

    @Bean("publish")
    public DirectComponent publish() {
        DirectComponent publish = new DirectComponent();
        return publish;
    }

    @Bean("eventstore")
    public DirectComponent eventStore() {
        DirectComponent eventStore = new DirectComponent();
        return eventStore;
    }

    @Bean("subscribe")
    public DirectComponent subscribe() {
        DirectComponent subscribe = new DirectComponent();
        return subscribe;
    }

    @Bean
    @Primary
    public DefaultErrorHandler deadLetterPublishingRecoverer(KafkaTemplate<?, ?> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (r, e) -> new TopicPartition("deadLetter", r.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 1));
    }

    @KafkaListener(topics = "deadLetter", autoStartup = "true", concurrency = "3")
    public void deadLetter(ConsumerRecord<?, ?> consumerRecord, Acknowledgment acknowledgment) {
        try {
            HashMap<String, String> deadLetterDetail = new HashMap<>();
            Iterator<Header> headerIterator = consumerRecord.headers().iterator();
            while (headerIterator.hasNext()) {
                Header header = headerIterator.next();
                deadLetterDetail.put(header.key(), new String(header.value()));
            }
            if (deadLetterDetail.get("context").equals(environment.getProperty("spring.application.name"))) {
                ErrorMessage errorMessage = new ErrorMessage();
                errorMessage.setMessageId(Long.valueOf(deadLetterDetail.get("messageId")));
                errorMessage.setSender(deadLetterDetail.get("sender"));
                errorMessage.setTopic(deadLetterDetail.get("kafka_dlt-original-topic")); // 原始TOPIC
                errorMessage.setEvent(deadLetterDetail.get("event"));
                errorMessage.setMappingMap(deadLetterDetail.get("mappingMap"));
                errorMessage.setContent(consumerRecord.value().toString());
                errorMessage.setGroupId(deadLetterDetail.get("kafka_dlt-original-consumer-group")); // 原始消费者组ID
                errorMessage.setErrorCount(1);
                errorMessage.setErrorStatus("STOP");

//                // todo，test，模拟1/2死信重发
//                if (System.currentTimeMillis() % 2 == 0) {
//                    errorMessage.setErrorStatus("WAITING");
//                    // todo，test，模拟1/2死信重发1/2到无关组
//                    if (System.currentTimeMillis() % 2 == 0) {
//                        errorMessage.setGroupId("test");
//                    }
//                }

                errorMessage.setErrorMessage(deadLetterDetail.get("kafka_dlt-exception-message")); // 异常信息
                producerTemplate.requestBody("subscribe://dead-message", errorMessage);
                log.info("死信保存: " + JsonUtil.writeValueAsString(deadLetterDetail));
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            acknowledgment.nack(Duration.ofMillis(0L));
        }
    }
}
