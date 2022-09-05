package io.micrc.core.configuration.springboot;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

@Order(Ordered.LOWEST_PRECEDENCE)
public class MicrcEnvironmentProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public MicrcEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("Processing Environment For Micrc...");
        Properties properties = new Properties();
        PropertiesPropertySource source = new PropertiesPropertySource("micrc", properties);
        environment.getPropertySources().addLast(source);
    }
}
