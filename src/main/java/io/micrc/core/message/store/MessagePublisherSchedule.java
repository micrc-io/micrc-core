package io.micrc.core.message.store;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 消息推送调度器
 *
 * @author hyosunghan
 * @date 2022/12/01 14:39
 * @since 0.0.1
 */
public class MessagePublisherSchedule {

    @EndpointInject("eventstore://sender")
    private ProducerTemplate sender;

    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 1)
    @SchedulerLock(name = "MessagePublisherSchedule")
    public void adapt() {
        sender.sendBody(System.currentTimeMillis());
    }

}
