package io.micrc.core.configuration.springboot;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class EmbeddedServerLoggingApplicationRunner implements ApplicationRunner {

    @Autowired
    private Environment env;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Optional<String> profileStr = Optional.ofNullable(env.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        if (profiles.contains("default") || profiles.contains("local")) {
            loggingEmbeddedRabbitmq();
            loggingEmbeddedRedis();
        }
    }

    private void loggingEmbeddedRabbitmq() {
        String username = env.getProperty("spring.rabbitmq.username");
        String password = env.getProperty("spring.rabbitmq.password");
        String server = env.getProperty("spring.rabbitmq.host") + ":" + env.getProperty("spring.rabbitmq.port");
        String managerUrl = "http://localhost:" + env.getProperty("micrc.embedded.rabbitmq.httpPort");
        log.info("RabbitMQ server start on: {}. user/pass: {}/{}", server, username, password);
        log.info("RabbitMQ manager ui start on: {}. user/pass: {}/{}", managerUrl, username, password);
    }

    private void loggingEmbeddedRedis() {
        String username = "";
        String password = env.getProperty("spring.redis.password");
        String server = env.getProperty("spring.redis.host") + ":" + env.getProperty("spring.redis.port");
        // String managerUrl = "http://localhost:" + env.getProperty("spring.redis.port"); // 查找redis web集成spring
        log.info("Redis server start on: {}. user/pass: {}/{}", server, username, password);
        // log.info("Redis manager ui start on: {}. user/pass: {}/{}", managerUrl, username, password); // 查找redis web
    }
}
