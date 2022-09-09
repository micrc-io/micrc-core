package io.micrc.core.configuration.springboot;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class MicrcEnvironmentProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public MicrcEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("Processing Environment For Micrc...");
        Collection<String> profiles = getProfiles(environment);
        Properties properties = this.loadMicrcProperties();
        properties.setProperty("application.profiles",
            StringUtils.arrayToCommaDelimitedString(profiles.toArray(new String[]{})));
        log.info("Micrc Application Profiles: " + profiles);
        if (profiles.contains("default")) {
            log.info("Local Default Environment, Disable Kubernetes Configmap Support. ");
            properties.setProperty("spring.cloud.kubernetes.enabled", "false");
        }
        PropertiesPropertySource source = new PropertiesPropertySource("micrc", properties);
        environment.getPropertySources().addLast(source);
    }

    private Properties loadMicrcProperties() {
        Resource resource = new ClassPathResource("micrc.properties");
        Properties properties = new Properties();
        try {
            properties.load(resource.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Unable loading properties from 'micrc.properties'", e);
        }
        return properties;
    }

    private Collection<String> getProfiles(Environment env) {
        String activeProfilesConfig = env.getProperty("spring.profiles.active");
        if (StringUtils.hasText(activeProfilesConfig)) {
            return Arrays.asList(activeProfilesConfig.split(",")).stream()
                    .map(String::trim).collect(Collectors.toSet());
        }
        return Arrays.asList(env.getDefaultProfiles());
    }
}
