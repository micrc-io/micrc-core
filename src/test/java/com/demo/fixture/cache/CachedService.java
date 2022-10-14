package com.demo.fixture.cache;

import io.micrc.core.annotations.application.businesses.BusinessesService;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import io.micrc.core.annotations.application.businesses.BusinessesExecution;
import io.micrc.core.application.businesses.ApplicationBusinessesService;

@BusinessesService
public interface CachedService extends ApplicationBusinessesService<CachedService.Command> {
    void execute(Command command);

    public static class Command {
        @Override
        public String toString() {
            return "Command []";
        }
    }

    @Component
    public static class CachedServerRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("businesses:CachedService")
                .log("被缓存的方法执行，看到意味着缓存未生效")
                .log("执行逻辑")
                .setBody().constant("{json result}")
                .end();
        }
    }

    @Component("CachedService")
    public static class CachedServiceImpl implements CachedService {
        @BusinessesExecution
        @Override
        public void execute(Command command) {
            // System.out.println("被缓存的方法执行，看到意味着缓存未生效");
        }
    }
}
