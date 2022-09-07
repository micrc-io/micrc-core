package io.micrc.core.message.springboot;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;

@Configuration
public class MessageAutoConfiguration {
    
    @Profile({ "local" })
    @Bean
    public ConnectionFactory connectionFactory() {
        return new CachingConnectionFactory(new MockConnectionFactory());
    }

}
