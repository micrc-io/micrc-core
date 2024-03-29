//package io.micrc.core.message.rabbit.springboot;
//
//import com.rabbitmq.client.ShutdownSignalException;
//import io.micrc.core.message.rabbit.RabbitMessageConsumeRouterExecution;
//import io.micrc.core.message.rabbit.RabbitMessageRouteConfiguration;
//import io.micrc.core.message.rabbit.store.RabbitEventMessage;
//import io.micrc.core.message.rabbit.store.RabbitIdempotentMessage;
//import io.micrc.core.message.rabbit.store.RabbitMessagePublisherSchedule;
//import io.micrc.core.message.rabbit.tracking.RabbitErrorMessage;
//import io.micrc.core.message.rabbit.tracking.RabbitErrorMessage.DeadMessageResolver;
//import io.micrc.core.message.rabbit.tracking.RabbitMessageCallback;
//import io.micrc.core.message.rabbit.tracking.RabbitMessageTracker;
//import org.apache.camel.component.direct.DirectComponent;
//import org.apache.camel.spring.boot.CamelAutoConfiguration;
//import org.springframework.amqp.core.*;
//import org.springframework.amqp.rabbit.connection.Connection;
//import org.springframework.amqp.rabbit.connection.ConnectionListener;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.boot.autoconfigure.AutoConfigureAfter;
//import org.springframework.boot.autoconfigure.domain.EntityScan;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Import;
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
//
//
///**
// * message auto configuration. 注册publish/subscribe组件，创建消息队列mock connection
// *
// * @author weiguan
// * @date 2022-09-06 19:29
// * @since 0.0.1
// */
////@Order(Integer.MAX_VALUE)
//@AutoConfigureAfter(CamelAutoConfiguration.class)
//@Configuration
//@Import({
//        RabbitMessageRouteConfiguration.class,
//        RabbitMessagePublisherSchedule.class,
//        RabbitMessageCallback.class,
//        RabbitMessageTracker.class,
//        RabbitEventMessage.class,
//        RabbitErrorMessage.class,
//        RabbitIdempotentMessage.class,
//        DeadMessageResolver.class,
//        RabbitMessageConsumeRouterExecution.class
//})
//@EntityScan(basePackages = {"io.micrc.core.message.rabbit.store", "io.micrc.core.message.rabbit.tracking"})
//@EnableJpaRepositories(basePackages = {"io.micrc.core.message.rabbit.store", "io.micrc.core.message.rabbit.tracking"})
//public class RabbitMessageAutoConfiguration implements ApplicationRunner {
//
//    public static final String DEAD_LETTER_EXCHANGE_KEY = "x-dead-letter-exchange";
//
//    public static final String DEAD_LETTER_EXCHANGE_ROUTING_KEY = "x-dead-letter-routing-key";
//
//    public static final String DEAD_LETTER_EXCHANGE_NAME = "dead-message";
//
//    public static final String DEAD_LETTER_QUEUE_NAME = "error";
//
//    public static final String DEAD_LETTER_ROUTING_KEY = "error";
//
//    @Autowired
//    private RabbitMessageCallback rabbitMessageCallback;
//
//    @Autowired
//    private RabbitTemplate template;
//
//    @Bean
//    public Exchange deadLetterExchange() {
//        return ExchangeBuilder
//                .directExchange(DEAD_LETTER_EXCHANGE_NAME)
//                .durable(true)
//                .build();
//    }
//
//    @Bean
//    public Queue deadLetterQueue() {
//        return QueueBuilder
//                .durable(DEAD_LETTER_QUEUE_NAME)
//                .build();
//    }
//
//    @Bean
//    public Binding deadLetterBinding(Queue deadLetterQueue, Exchange deadLetterExchange) {
//        return BindingBuilder
//                .bind(deadLetterQueue)
//                .to(deadLetterExchange)
//                .with(DEAD_LETTER_ROUTING_KEY)
//                .noargs();
//    }
//
//    @Bean("publish")
//    public DirectComponent publish() {
//        DirectComponent publish = new DirectComponent();
//        return publish;
//    }
//
//    @Bean("subscribe")
//    public DirectComponent subscribe() {
//        DirectComponent subscribe = new DirectComponent();
//        return subscribe;
//    }
//
//    @Bean("eventstore")
//    public DirectComponent eventStore() {
//        DirectComponent eventStore = new DirectComponent();
//        return eventStore;
//    }
//
//    @Override
//    public void run(ApplicationArguments args) throws Exception {
//        template.setMandatory(true);
//        // 设置出错回调
//        template.setReturnsCallback(rabbitMessageCallback);
//        // 设置返回回调
//        template.setConfirmCallback(rabbitMessageCallback);
//        // 设置在链接时的监听器
//        template.getConnectionFactory().addConnectionListener(new ConnectionListener() {
//            @Override
//            public void onCreate(Connection connection) {
//            }
//
//            @Override
//            public void onShutDown(ShutdownSignalException signal) {
//                ConnectionListener.super.onShutDown(signal);
//            }
//
//            @Override
//            public void onFailed(Exception exception) {
//                ConnectionListener.super.onFailed(exception);
//            }
//        });
//    }
//
//
////    @RabbitListener(queues = RabbitMQStudentCourseTopicConfig.DEAD_LETTER_QUEUE)
////    public void doChooseCourse(Message message, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag, Channel channel)throws IOException {
////        System.out.println("收到死信消息:" + new String(message.getBody()));
////        channel.basicAck(deliveryTag,false);
////    }
//}
