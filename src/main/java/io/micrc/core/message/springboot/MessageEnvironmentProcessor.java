package io.micrc.core.message.springboot;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * message env processor.
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-01 15:36
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
        envForRabbitMQ(profiles, properties);
        PropertiesPropertySource source = new PropertiesPropertySource("micrc-message", properties);
        environment.getPropertySources().addLast(source);
    }

    private void envForRabbitMQ(List<String> profiles, Properties properties) {
        if (!profiles.contains("default")) {
            properties.setProperty("embedded.rabbitmq.enabled", "false");
        }

        if (profiles.contains("default")) {
            log.info("Embedded rabbitmq server configuration for profile: 'default'");
            // embedded rabbitmq
            properties.setProperty("embedded.rabbitmq.enabled", "true");
            properties.setProperty("embedded.rabbitmq.reuseContainer", "true");
            properties.setProperty("embedded.rabbitmq.password", "rabbitmq");
            properties.setProperty("embedded.rabbitmq.vhost", "/");
            properties.setProperty("embedded.rabbitmq.dockerImage", "rabbitmq:3.9-management");
            properties.setProperty("embedded.rabbitmq.waitTimeoutInSeconds", "60");

            properties.setProperty("micrc.embedded.rabbitmq.httpPort", "${embedded.rabbitmq.httpPort}");
        }
        if (profiles.contains("default")) {
            // default rabbitmq config
            properties.setProperty("spring.rabbitmq.host", "${embedded.rabbitmq.host}");
            properties.setProperty("spring.rabbitmq.port", "${embedded.rabbitmq.port}");
            properties.setProperty("spring.rabbitmq.username", "${embedded.rabbitmq.user}");
            properties.setProperty("spring.rabbitmq.password", "${embedded.rabbitmq.password}");
            properties.setProperty("spring.rabbitmq.virtual-host", "${embedded.rabbitmq.vhost}");
        } else {
            // k8s集群中读取的secret中的host，port和passwd
            properties.setProperty("spring.rabbitmq.host", "${broker.host}");
            properties.setProperty("spring.rabbitmq.port", "${broker.port}");
            properties.setProperty("spring.rabbitmq.username", "${broker.user}");
            properties.setProperty("spring.rabbitmq.password", "${broker.pass}");
        }

        properties.setProperty("spring.rabbitmq.template.mandatory", "true");
        properties.setProperty("spring.rabbitmq.publisher-confirm-type", "correlated");
        properties.setProperty("spring.rabbitmq.publisher-returns", "true");
        properties.setProperty("spring.rabbitmq.listener.simple.acknowledge-mode", "manual");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.enable", "true");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.multiplier", "3");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.max-attempts", "3");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.max-interval", "15000ms");
        properties.setProperty("spring.rabbitmq.listener.simple.retry.initial-interval", "1000ms");
    }
}
