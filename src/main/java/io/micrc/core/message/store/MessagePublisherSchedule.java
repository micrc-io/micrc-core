package io.micrc.core.message.store;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.DefaultCamelContext;
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

    @Autowired
    private CamelContext camelContext;

    @EndpointInject
    private ProducerTemplate producerTemplate;

    @Scheduled(initialDelay = 1, fixedDelay = 1)
    @SchedulerLock(name = "MessagePublisherSchedule")
    public void adapt() {
        if (!isStarted("eventstore://sender")) {
            return;
        }
        producerTemplate.sendBody("eventstore://sender", System.currentTimeMillis());
    }

    @Scheduled(initialDelay = 1, fixedDelay = 1)
    @SchedulerLock(name = "MessageCleanSchedule")
    public void clean() {
        if (!isStarted("eventstore://clear")) {
            return;
        }
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        if (profiles.contains("default") || profiles.contains("local")) {
            return;
        }
        producerTemplate.sendBody("eventstore://clear", System.currentTimeMillis());
    }

    private boolean isStarted(String routeId) {
        DefaultCamelContext defaultCamelContext = (DefaultCamelContext) camelContext;
        if (defaultCamelContext == null) {
            return false;
        }
        ServiceStatus routeStatus = defaultCamelContext.getRouteStatus(routeId);
        return routeStatus != null && routeStatus.isStarted();
    }

}
