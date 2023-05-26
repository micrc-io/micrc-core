package io.micrc.core.message.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
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

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        Properties properties = new Properties();
        envForKafka(profiles, properties);
        PropertiesPropertySource source = new PropertiesPropertySource("micrc-message", properties);
        environment.getPropertySources().addLast(source);
    }

    private void envForKafka(List<String> profiles, Properties properties) {
        if (!profiles.contains("default")) {
            properties.setProperty("embedded.kafka.enabled", "false");
        }

        if (profiles.contains("default")) {
            // embedded kafka
            properties.setProperty("embedded.kafka.enabled", "true");
            properties.setProperty("embedded.kafka.reuseContainer", "true");
            // properties.setProperty("embedded.kafka.dockerImage", "bitnami/kafka");
            // properties.setProperty("embedded.kafka.dockerImageVersion", "3.3.1-debian-11-r22");
            properties.setProperty("embedded.kafka.waitTimeoutInSeconds", "60");
            properties.setProperty("embedded.kafka.topicsToCreate", "deadLetter");

            properties.setProperty("micrc.embedded.kafka.brokerList", "${embedded.kafka.brokerList}");
        }
        if (profiles.contains("default")) {
            // default kafka config
            // use plaintext
            properties.setProperty("spring.kafka.bootstrap-servers", "${embedded.kafka.brokerList}");
            // use saslPlaintext
            // properties.setProperty("spring.kafka.bootstrap.servers", "${embedded.kafka.saslPlaintext.brokerList}");
            // properties.setProperty("spring.kafka.sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${embedded.kafka.saslPlaintext.user}\" password=\"${embedded.kafka.saslPlaintext.password}\"");

        } else {
            // k8s集群中读取的secret中的host，port和passwd
            properties.setProperty("spring.kafka.bootstrap-servers", "${broker.host}:${broker.port}");
            //properties.setProperty("spring.kafka.sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${kafka.user}\" password=\"${kafka.password}\"");
        }
        // use plaintext
        // properties.setProperty("spring.kafka.security.protocol", "SASL_PLAINTEXT");
        // properties.setProperty("spring.kafka.sasl.mechanism", "PLAIN");
        // 重试次数
        properties.setProperty("spring.kafka.producer.retries", "3");
        // ACK应答模式
        properties.setProperty("spring.kafka.producer.acks", "all");
        // 批量发送的消息数量
        properties.setProperty("spring.kafka.producer.batch-size", "1000");
        // 32MB的批处理缓冲区
        properties.setProperty("spring.kafka.producer.buffer-memory", "33554432");
        // 默认消费者组
        Properties loadMicrcProperties = loadMicrcProperties();
        String applicationName = loadMicrcProperties.getProperty("spring.application.name");
        properties.setProperty("spring.kafka.consumer.group-id", applicationName);
        // 最早未被消费的offset
        properties.setProperty("spring.kafka.consumer.auto-offset-reset", "latest");
        // 批量一次最大拉取数据量
        properties.setProperty("spring.kafka.consumer.max-poll-records", "4000");
        // 是否自动提交
        properties.setProperty("spring.kafka.consumer.enable-auto-commit", "false");
        // 批消费并发量，小于或等于Topic的分区数
        properties.setProperty("spring.kafka.consumer.batch.concurrency", "3");
        // 监听器设置手动应答
        properties.setProperty("spring.kafka.listener.ack-mode", "MANUAL_IMMEDIATE");
        // 日志级别
        properties.setProperty("logging.level.org.apache.kafka", "WARN");
    }

    private Properties loadMicrcProperties() {
        Resource resource = new ClassPathResource("micrc.properties");
        Properties properties = new Properties();
        try {
            properties.load(resource.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Unable loading properties from 'micrc.properties'", e);
        }
        return properties;
    }
}
