package io.micrc.core.message.springboot;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.springrabbit.SpringRabbitMQComponent;
import org.apache.camel.component.springrabbit.springboot.SpringRabbitMQComponentAutoConfiguration;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;

@Configuration
@EnableAutoConfiguration(exclude = SpringRabbitMQComponentAutoConfiguration.class)
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
        // subscriber.setDeadLetterExchange("");
        // subscriber.setDeadLetterExchangeType("");
        // subscriber.setDeadLetterQueue("");
        // subscriber.setDeadLetterRoutingKey("");
        // subscriber.setMaximumRetryAttempts(5); // 消息处理失败的重试次数
        // subscriber.setRejectAndDontRequeue(true); // 是否拒绝重新排队失败消息，而发到dlq
        // subscriber.setRetryDelay(1000); // 失败消息重新投递延迟
        // subscriber.setConcurrentConsumers(1); // 并发消费者数量
        // subscriber.setMaxConcurrentConsumers();
        // subscriber.setMessageListenerContainerType();
        // subscriber.setPrefetchCount(250); // 预获取消息数量
        // subscriber.setRetry(); // 自定义重试逻辑 RetryOperationsInterceptor
        // subscriber.setShutdownTimeout(5000); // 等待container停止的超时时间
        return subscriber;
    }

    @Bean
    public RoutesBuilder rabbitmqTest() {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").setBody(simple("test")).log("sending").to("publish:ex1?routingKey=tt");
                from("subscribe:ex1?routingKey=tt").log("test");
                from("timer:test?delay=10000&repeatCount=1").log("starting...").to("direct:start");
            }
            
        };
    }
}
