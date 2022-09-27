package io.micrc.core.message.springboot;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * message env processor.
 *
 * @author weiguan
 * @date 2022-09-01 15:36
 * @since 0.0.1
 */
public class MessageEnvironmentProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public MessageEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        Properties properties = new Properties();
        envForRabbitMQ(profiles, properties, environment.getPropertySources());
        PropertiesPropertySource source = new PropertiesPropertySource("micrc-message", properties);
        environment.getPropertySources().addLast(source);
    }

    private void envForRabbitMQ(List<String> profiles, Properties properties, MutablePropertySources propertySources) {
        properties.setProperty("spring.rabbitmq.template.mandatory", "true");
        properties.setProperty("spring.rabbitmq.publisher-confirm-type", "correlated");
        properties.setProperty("spring.rabbitmq.publisher-returns", "true");
        properties.setProperty("spring.rabbitmq.listener.simple.acknowledge-mode", "manual");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.enable", "true");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.multiplier", "3");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.max-attempts", "3");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.max-interval", "15000ms");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.initial-interval", "1000ms");
        if (profiles.contains("local") || profiles.contains("default")) {
            properties.setProperty("spring.rabbitmq.host", "127.0.0.1");
            properties.setProperty("spring.rabbitmq.port", "5672");
            properties.setProperty("spring.rabbitmq.username", "rabbit");
            properties.setProperty("spring.rabbitmq.password", "rabbit");
        }
    }
}
