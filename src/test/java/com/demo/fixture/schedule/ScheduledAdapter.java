package com.demo.fixture.schedule;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.scheduling.annotation.Async;
// import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.demo.fixture.cache.CachedService;

// import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

public interface ScheduledAdapter {
    void adapte();

    @Component
    public static class ScheduleAdapterRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("schedule:ScheduledAdapter")
                .log("${date:_command:pattern_}")
                .setBody().constant("{json param}")
                .to("businesses:CachedService");
        }
    }

    @Component("ScheduledAdapter")
    public static class ScheduledAdapterImpl implements ScheduledAdapter {

        @Autowired
        private CachedService cachedService;

        // @Async
        // @Scheduled(initialDelay = 10000, fixedDelay = 3000)
        // @SchedulerLock(name = "ScheduledAdapter")
        @Override
        public void adapte() {
            System.out.println(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ">>执行service");
            cachedService.execute(new CachedService.Command());
        }

    }
}
