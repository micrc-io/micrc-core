package com.demo.fixture.cache;

import org.springframework.stereotype.Component;

public interface CachedService {
    void exec(Command command);

    class Command {}

    @Component("CachedService")
    public static class CachedServiceImpl implements CachedService {
        @Override
        public void exec(Command command) {
            
        }
    }
}
