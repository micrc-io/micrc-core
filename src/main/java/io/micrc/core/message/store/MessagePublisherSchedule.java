package io.micrc.core.message.store;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 消息推送调度器
 *
 * @author hyosunghan
 * @date 2022/12/01 14:39
 * @since 0.0.1
 */
public class MessagePublisherSchedule {

    @Autowired
    private Environment environment;

    @EndpointInject("eventstore://sender")
    private ProducerTemplate sender;

    @EndpointInject("eventstore://clear")
    private ProducerTemplate clear;

    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 1)
    @SchedulerLock(name = "MessagePublisherSchedule")
    public void adapt() {
        sender.sendBody(System.currentTimeMillis());
    }

    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 1)
    @SchedulerLock(name = "MessageCleanSchedule")
    public void clean() {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        if (profiles.contains("default") || profiles.contains("local")) {
            return;
        }
        clear.sendBody(System.currentTimeMillis());
    }

}
