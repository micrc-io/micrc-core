//package io.micrc.core.message.rabbit.store;
//
//import org.apache.camel.EndpointInject;
//import org.apache.camel.ProducerTemplate;
//
//public class RabbitMessagePublisherSchedule {
//
//    @EndpointInject("eventstore://sender")
//    private ProducerTemplate sender;
//
////    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 3000)
////    @SchedulerLock(name = "MessagePublisherSchedule")
//    public void adapt() {
//        sender.sendBody(System.currentTimeMillis());
//    }
//
//}
