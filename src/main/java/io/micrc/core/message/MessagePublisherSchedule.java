package io.micrc.core.message;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

public interface MessagePublisherSchedule {
    void adapt();

    @Component("MessagePublisherSchedule")
    public static class ScheduledAdapterImpl implements MessagePublisherSchedule {

        @EndpointInject("eventstore://sender")
        private ProducerTemplate sender;

        @Scheduled(initialDelay = 10000, fixedDelay = 3000)
        @SchedulerLock(name = "MessagePublisherSchedule")
        @Override
        public void adapt() {
            sender.sendBody(System.currentTimeMillis());
        }

    }
}
