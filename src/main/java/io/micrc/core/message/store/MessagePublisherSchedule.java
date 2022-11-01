package io.micrc.core.message.store;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public class MessagePublisherSchedule {

    @EndpointInject("eventstore://sender")
    private ProducerTemplate sender;

    @Scheduled(fixedDelay = 3000)
    @SchedulerLock(name = "MessagePublisherSchedule")
    public void adapt() {
        sender.sendBody(System.currentTimeMillis());
    }

}
