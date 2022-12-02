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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;


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
}
