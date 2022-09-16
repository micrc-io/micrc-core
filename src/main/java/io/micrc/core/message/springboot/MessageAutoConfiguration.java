package io.micrc.core.message.springboot;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.springrabbit.SpringRabbitMQComponent;
import org.apache.camel.component.springrabbit.springboot.SpringRabbitMQComponentAutoConfiguration;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;

import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.message.MessageRouteConfiguration;

/**
 * message auto configuration. 注册publish/subscribe组件，创建消息队列mock connection
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-06 19:29
 */
@Configuration
@EnableAutoConfiguration(exclude = SpringRabbitMQComponentAutoConfiguration.class)
@Import({ MessageRouteConfiguration.class })
public class MessageAutoConfiguration {

    @Bean
    @Profile({ "default", "local" })
    public ConnectionFactory connectionFactory() {
        return new CachingConnectionFactory(new MockConnectionFactory());
    }

    @Bean("publish")
    public SpringRabbitMQComponent publish(AmqpAdmin amqpAdmin, ConnectionFactory connectionFactory) {
        SpringRabbitMQComponent publisher = new SpringRabbitMQComponent();
        publisher.setConnectionFactory(connectionFactory);
        publisher.setAmqpAdmin(amqpAdmin);
        publisher.setAutoDeclare(true);
        // publisher.setAllowNullBody(false); // 是否允许空消息
        return publisher;
    }

    @Bean("subscribe")
    public SpringRabbitMQComponent subscribe(AmqpAdmin amqpAdmin, ConnectionFactory connectionFactory) {
        SpringRabbitMQComponent subscriber = new SpringRabbitMQComponent();
        subscriber.setConnectionFactory(connectionFactory);
        subscriber.setAmqpAdmin(amqpAdmin);
        subscriber.setTestConnectionOnStartup(true);
        subscriber.setAutoDeclare(true);
        subscriber.setAutoStartup(true);
        // subscriber.setBridgeErrorHandler(true); // consumer内部异常会委托给路由错误处理器处理，自定义路由异常处理器
        // subscriber.setDeadLetterExchange(""); // dlq
        // subscriber.setDeadLetterExchangeType(""); // dlq
        // subscriber.setDeadLetterQueue(""); // dlq
        // subscriber.setDeadLetterRoutingKey(""); // dlq
        // subscriber.setMaximumRetryAttempts(5); // 消息处理失败的重试次数
        // subscriber.setRejectAndDontRequeue(true); // 是否拒绝重新排队失败消息，而发到dlq
        // subscriber.setRetryDelay(1000); // 失败消息重新投递延迟
        // subscriber.setConcurrentConsumers(1); // 并发消费者数量
        // subscriber.setMaxConcurrentConsumers(); // 最大并发消费者?
        // subscriber.setMessageListenerContainerType(); // listener container type? DMLC - SMLC
        // subscriber.setPrefetchCount(250); // 预获取消息数量
        // subscriber.setRetry(); // 自定义重试逻辑 RetryOperationsInterceptor
        // subscriber.setShutdownTimeout(5000); // 等待container停止的超时时间
        return subscriber;
    }

    @Bean
    public RoutesBuilder rabbitmqTest() {
        return new MicrcRouteBuilder() {
            @Override
            public void configureRoute() throws Exception {
                from("direct:rabbitmqTest").setBody(simple("test"))
                    .log("sending")
                    .to("publish:ex1?routingKey=tt");
                from("subscribe:ex1?routingKey=tt")
                    .log("msg test done.");
                from("timer:rabbitmqTest?delay=10000&repeatCount=1")
                    .log("starting rabbitmqTest...").to("direct:rabbitmqTest");
            }
            
        };
    }
}
