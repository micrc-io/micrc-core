package io.micrc.core.message.springboot;

import io.micrc.core.message.MessageConsumeRouterExecution;
import io.micrc.core.message.MessageRouteConfiguration;
import io.micrc.core.message.store.MessagePublisherSchedule;
import io.micrc.core.message.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.*;


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
public class MessageAutoConfiguration implements BeanFactoryPostProcessor, EnvironmentAware, ApplicationContextAware {

    private ApplicationContext applicationContext;

    private Environment environment;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setEnvironment(@NotNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] providers = obtainProvider();
        Arrays.stream(providers)
                .filter(provider -> !provider.isEmpty())
                .forEach(provider -> {
                    // ConcurrentKafkaListenerContainerFactory
                    HashMap<String, Object> consumerMap = new HashMap<>();
                    consumerMap.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "${" + provider + "_broker_host}:${" + provider + "_broker_port}");
                    ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerMap);
                    ConcurrentKafkaListenerContainerFactory<String, String> concurrentKafkaListenerContainerFactory = new ConcurrentKafkaListenerContainerFactory<>();
                    concurrentKafkaListenerContainerFactory.setConsumerFactory(consumerFactory);
                    beanFactory.registerSingleton("kafkaListenerContainerFactory-" + provider, concurrentKafkaListenerContainerFactory);
                    // KafkaTemplate
                    HashMap<String, Object> producerMap = new HashMap<>();
                    producerMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "${" + provider + "_broker_host}:${" + provider + "_broker_port}");
                    ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerMap);
                    KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
                    beanFactory.registerSingleton("kafkaTemplate-" + provider, kafkaTemplate);
                });
    }

    private String[] obtainProvider() {
        String providersString = (String) ((ConfigurableEnvironment)environment).getSystemEnvironment().getOrDefault( "BROKER_PROVIDERS", "");
        return providersString.split(",");
    }

    @EndpointInject
    private ProducerTemplate producerTemplate;

    @Bean("clean")
    public DirectComponent clean() {
        DirectComponent clean = new DirectComponent();
        return clean;
    }

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
    public DefaultErrorHandler deadLetterPublishingRecoverer() {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        KafkaTemplate<String, String> kafkaTemplate;
        if (profiles.contains("default")) {
            kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate.class);
        } else {
            kafkaTemplate = applicationContext.getBean("kafkaTemplate-public", KafkaTemplate.class);
        }

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
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
            if (deadLetterDetail.get("senderHost").equals(environment.getProperty("micrc.x-host"))) {
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
                errorMessage.setErrorMessage(deadLetterDetail.get("kafka_dlt-exception-message")); // 异常信息
                producerTemplate.requestBody("subscribe://dead-message", errorMessage);
                log.info("死信保存: " + errorMessage.getMessageId());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            acknowledgment.nack(Duration.ofMillis(0L));
        }
    }
}
