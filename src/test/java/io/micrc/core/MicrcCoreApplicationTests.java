package io.micrc.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;

@SpringBootTest(classes = { TestApplication.Application.class, PersistenceAutoConfiguration.class })
class MicrcCoreApplicationTests {

    @Test
    void contextLoads() {
        assertTrue(true);
    }

}

class TestApplication {
    @Configuration
    @EnableAutoConfiguration
    @EnableMicrcSupport
    public static class Application {}
}
