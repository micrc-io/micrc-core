package io.micrc.core.persistence.springboot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceEnvironmentProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public PersistenceEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("Processing Environment For Persistence Of Micrc...");
        List<String> profiles = new ArrayList<>();
        profiles.addAll(Arrays.asList(environment.getDefaultProfiles()));
        profiles.addAll(Arrays.asList(environment.getActiveProfiles()));

        Properties properties = this.loadMicrcProperties();
        envForH2Embedded(profiles, properties);
        envForLiquibase(profiles, properties);

        PropertiesPropertySource source = new PropertiesPropertySource("micrc-persistence", properties);
        environment.getPropertySources().addLast(source);
    }

    private Properties loadMicrcProperties() {
        Resource resource = new ClassPathResource("micrc.properties");
        Properties properties = new Properties();
        try {
            properties.load(resource.getInputStream());
        } catch (IOException e) {
            log.error("Unable loading properties from 'micrc.properties'", e);
        }
        return properties;
    }
    
    private void envForH2Embedded(List<String> profiles, Properties properties) {
        if (profiles.contains("local") || profiles.contains("default")) {
            properties.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
            properties.setProperty("spring.h2.console.enabled", "true");
            properties.setProperty("spring.h2.console.path", "/h2-console");
        }
        if (profiles.contains("local")) { // TODO 本地集群环境，h2内存库/mysql库？
            properties.setProperty("spring.h2.console.settings.trace", "false");
            properties.setProperty("spring.h2.console.settings.web-allow-others", "true");
        }
        if (profiles.contains("default")) {
            properties.setProperty("spring.datasource.url", "jdbc:h2:mem:db;MODE=MYSQL");
            properties.setProperty("spring.datasource.username", "sa");
            properties.setProperty("spring.datasource.password", "pass");
            properties.setProperty("spring.h2.console.settings.trace", "true");
            properties.setProperty("spring.h2.console.settings.web-allow-others", "false");
        }
    }

    private void envForLiquibase(List<String> profiles, Properties properties) {
        String appVersion = properties.getProperty("application.version");
        if (!StringUtils.hasText(appVersion)) {
            throw new IllegalStateException(
                "Unable find application version. "
                + "application.version=${version} must exists in micrc.properties on classpath");
        }
        properties.setProperty("spring.liquibase.enabled", "false");
        properties.setProperty("spring.liquibase.changeLog", "db/changelog/master.yaml");
        properties.setProperty("spring.liquibase.parameters.version", appVersion);

        if (profiles.contains("dbinit") || profiles.contains("default")) {
            log.debug("Active Profile: dbinit\nEnable liquibase for init of db");
            // 默认关闭，当dbinit/default profile active时打开. 使liquibase执行数据库初始化
            // 仅在默认本地开发环境（开发机本地 - default）时有效
            // 仅在整个系统作为数据库初始化系统时有效
            properties.setProperty("spring.liquibase.enabled", "true");
        }
    }
}
