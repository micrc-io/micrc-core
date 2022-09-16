package io.micrc.core.cache;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class CachedRouterWrapper {
    @Cacheable("testCache")
    public String exec(String uri, String param) {
        System.out.println("缓存的方法: " + uri + ", " + param);
        return "test CachedRouterWrapper";
    }
}
