package io.micrc.core.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.MicrcApplication;
import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;

@SpringBootTest(properties = { "application.version=0.0.1" }, classes = { PersistenceTest.Application.class, PersistenceAutoConfiguration.class })
public class PersistenceTest {

    @Test
    void contextLoads() {
        assertTrue(true);
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableMicrcSupport
    public static class Application {}

    @SpringBootApplication
    @EnableMicrcSupport
    public static class Application1 {
        public static void main(String[] args) {
            List<String> argList = new ArrayList<>(Arrays.asList(args));
            // argList.add("local");
            MicrcApplication.run(Application1.class, argList.toArray(String[]::new));
        }
    }
}
