package io.micrc.core.persistence.springboot;

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
public class PersistenceEnvironmentProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public PersistenceEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Collection<String> profiles = getProfiles(environment);
        Properties properties = this.loadMicrcProperties();
        envForH2Embedded(profiles, properties);
        envForLiquibase(profiles, properties);
        envForJPA(properties);

        PropertiesPropertySource source = new PropertiesPropertySource("micrc-persistence", properties);
        environment.getPropertySources().addLast(source);
    }

    private Collection<String> getProfiles(Environment env) {
        String activeProfilesConfig = env.getProperty("spring.profiles.active");
        if (StringUtils.hasText(activeProfilesConfig)) {
            return Arrays.asList(activeProfilesConfig.split(",")).stream()
                    .map(String::trim).collect(Collectors.toSet());
        }
        return Arrays.asList(env.getDefaultProfiles());
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
    
    private void envForH2Embedded(Collection<String> profiles, Properties properties) {
        if (profiles.contains("local") || profiles.contains("default")) { // 只有local和default环境使用h2
            properties.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
            properties.setProperty("spring.datasource.url", "jdbc:h2:mem:db;MODE=MYSQL");
            properties.setProperty("spring.datasource.username", "sa");
            properties.setProperty("spring.datasource.password", "");
            properties.setProperty("spring.h2.console.enabled", "true");
            properties.setProperty("spring.h2.console.path", "/h2-console");
            properties.setProperty("spring.h2.console.settings.web-allow-others", "true");
            properties.setProperty("spring.h2.console.settings.trace", "false");
        }
    }

    private void envForLiquibase(Collection<String> profiles, Properties properties) {
        properties.setProperty("spring.liquibase.enabled", "false"); // 默认先关闭
        // 客户端系统应该在micrc.properties文件中定义application.version=${version}这个属性
        // 这个version会在gradle处理resources时通过project.properties进行expand，填充为当前build.gradle中的版本号
        // 这个版本号会带给liquibase使用，在使用内存库时，首选执行snapshot快照，然后执行版本号指定的changeset
        String appVersion = properties.getProperty("application.version");
        if (!StringUtils.hasText(appVersion)) {
            throw new IllegalStateException(
                "Unable find application version. "
                + "application.version=${version} must exists in micrc.properties on classpath");
        }
        // 用于master include内存库的changelog的main文件
        String mem = "";
        if (profiles.contains("default") || profiles.contains("local")) {
            mem = "-mem";
        }
        // 两种情况下，liquibase在app内打开
        // 1. 系统作为liquibase数据库迁移app时，独立打包成image在init container中初始化数据库
        // 2. 当系统profile为default(本地开发)时，系统自动通过snapshot和当前版本changelog每次启动时初始化数据库
        // note: local(本地集群)环境，也是使用dbinit独立image在init container中初始化数据库
        if (profiles.contains("dbinit") || profiles.contains("default")) {
            log.debug("Active Profile: dbinit\nEnable liquibase for init of db");
            properties.setProperty("spring.liquibase.enabled", "true");
        }
        properties.setProperty("spring.liquibase.changeLog", "db/master.yaml");
        properties.setProperty("spring.liquibase.parameters.version", appVersion);
        properties.setProperty("spring.liquibase.parameters.mem", mem);
        log.info(properties);
    }

    private void envForJPA(Properties properties) {
        properties.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.MySQLDialect");
        properties.setProperty("spring.jpa.open-in-view", "false");
    }
}
