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

/**
 * micro 全局环境配置，读取自定义micrc.properties文件注入配置，处理profiles、banner等
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-30 16:38
 */
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
        properties.setProperty("micrc.key", "io.micrc");
        properties.setProperty("application.profiles",
            StringUtils.arrayToCommaDelimitedString(profiles.toArray(new String[]{})));
        log.info("Micrc Application Profiles: " + profiles);
        if (!profiles.contains("default")) {
            log.info("Non Local Default Environment, Disable Test Container Support. ");
            properties.setProperty("embedded.containers.enabled", "false");
        }
        envForKubernetes(profiles, properties);
        envForActuator(profiles, properties);
        envForGracefulShutdown(properties);

        // TODO 处理banner: springboot版本，micrc版本，应用版本；micrc logo，应用logo；当前profile及描述
        PropertiesPropertySource source = new PropertiesPropertySource("micrc", properties);
        environment.getPropertySources().addLast(source);
    }

    private void envForKubernetes(Collection<String> profiles, Properties properties) {
        properties.setProperty("spring.cloud.kubernetes.discovery.enabled", "false");
        properties.setProperty("spring.cloud.kubernetes.leader.enabled", "false");
        properties.setProperty("spring.cloud.kubernetes.loadbalancer.enabled", "false");
        properties.setProperty("spring.cloud.kubernetes.reload.enabled", "true");
        // default环境关闭所有
        if (profiles.contains("default")) {
            properties.setProperty("spring.cloud.kubernetes.enabled", "false");
        }
        if (!profiles.contains("default")) {
            log.info("Kubernetes Environment, Enable Kubernetes Configmap And Secret Support. ");
            properties.setProperty("spring.cloud.kubernetes.config.enabled", "true");
            properties.setProperty("spring.cloud.kubernetes.config.fail-fast", "true");
            properties.setProperty("spring.cloud.kubernetes.config.include-profile-specific-sources", "false");
            properties.setProperty("spring.cloud.kubernetes.config.name",
                properties.getProperty("spring.application.name"));
            properties.setProperty("spring.cloud.kubernetes.secrets.enabled", "true");
            properties.setProperty("spring.cloud.kubernetes.secrets.fail-fast", "true");
            properties.setProperty("spring.cloud.kubernetes.secrets.paths", "/etc/secrets");
        }
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

    private void envForActuator(Collection<String> profiles, Properties properties) {
        properties.setProperty("management.server.port", "18080");
        // 默认关闭所有endpoints，按需开启
        properties.setProperty("management.endpoints.enabled-by-default", "false");
        properties.setProperty("management.endpoints.web.exposure.include", "*");
        // 开发环境打开全部endpoints
        if (profiles.contains("default") || profiles.contains("local") || profiles.contains("dev")) {
            properties.setProperty("management.endpoints.enabled-by-default", "true");
        }
        // graceful shutdown
        properties.setProperty("management.endpoint.shutdown.enabled", "true");
        // liveness and readiness probe
        properties.setProperty("management.endpoint.health.enabled", "true");
        properties.setProperty("management.endpoint.health.probes.enabled", "true");
        properties.setProperty("management.health.livenessState.enabled", "true");
        properties.setProperty("management.health.readinessState.enabled", "true");
    }

    private void envForGracefulShutdown(Properties properties) {
        properties.setProperty("server.shutdown", "graceful");
        properties.setProperty("spring.lifecycle.timeout-per-shutdown-phase", "30s");
    }
}
