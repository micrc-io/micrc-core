package io.micrc.core.message.springboot;

import io.micrc.core.message.MessageConsumeRouterExecution;
import io.micrc.core.message.MessageRouteConfiguration;
import io.micrc.core.message.store.MessagePublisherSchedule;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ErrorHandler;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;


/**
 * message auto configuration. 注册publish/subscribe组件
 *
 * @author weiguan
 * @date 2022-09-06 19:29
 * @since 0.0.1
 */
@AutoConfigureAfter(CamelAutoConfiguration.class)
@Configuration
@EnableKafka
@Import({
        MessageRouteConfiguration.class,
        MessagePublisherSchedule.class,
        MessageConsumeRouterExecution.class
})
@EntityScan(basePackages = {"io.micrc.core.message.store", "io.micrc.core.message.tracking"})
@EnableJpaRepositories(basePackages = {"io.micrc.core.message.store", "io.micrc.core.message.tracking"})
public class MessageAutoConfiguration {

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
    public ErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
        // <1> 创建 DeadLetterPublishingRecoverer 对象
        ConsumerRecordRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // <2> 创建 FixedBackOff 对象   设置重试间隔 0秒 次数为 1次
        BackOff backOff = new FixedBackOff(0L, 1L);
        // <3> 创建 SeekToCurrentErrorHandler 对象
        return new SeekToCurrentErrorHandler(recoverer, backOff);
    }
}
