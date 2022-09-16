package io.micrc.core.cache;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CachedRouterWrapper {

    @EndpointInject
    private ProducerTemplate template;

    @Cacheable("testCache")
    public String exec(String uri, String param) {
        log.info("CachedRouterWrapper - 进入方法，执行路由...[缓存未生效]");
        return template.requestBodyAndHeader(uri, param, "CacheWrapper", true, String.class);
    }
}
