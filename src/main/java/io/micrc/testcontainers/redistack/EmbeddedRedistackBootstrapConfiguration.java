package io.micrc.testcontainers.redistack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.MountableFile;

import com.playtika.test.common.spring.DockerPresenceBootstrapConfiguration;
import com.playtika.test.common.utils.ContainerUtils;
import com.playtika.test.common.utils.FileUtils;

import io.micrc.testcontainers.redistack.wait.DefaultRedistackClusterWaitStrategy;
import io.micrc.testcontainers.redistack.wait.RedistackStatusCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * bootstrap configuration
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-01 22:04
 */
@Slf4j
@Configuration
@ConditionalOnExpression("${embedded.containers.enabled:true}")
@AutoConfigureAfter(DockerPresenceBootstrapConfiguration.class)
@ConditionalOnProperty(name = "embedded.redistack.enabled", matchIfMissing = true)
@EnableConfigurationProperties(RedistackProperties.class)
@RequiredArgsConstructor
public class EmbeddedRedistackBootstrapConfiguration {
    
    private final static String REDISTACK_WAIT_STRATEGY_BEAN_NAME = "redistackStartupCheckStrategy";

    private final ResourceLoader resourceLoader;
    private final RedistackProperties properties;

    @Bean(name = REDISTACK_WAIT_STRATEGY_BEAN_NAME)
    @ConditionalOnMissingBean(name = REDISTACK_WAIT_STRATEGY_BEAN_NAME)
    @ConditionalOnProperty(name = "embedded.redistack.clustered", havingValue = "false", matchIfMissing = true)
    public WaitStrategy redistackStartupCheckStrategy(RedistackProperties properties) {
        return new RedistackStatusCheck(properties);
    }

    @Bean(name = REDISTACK_WAIT_STRATEGY_BEAN_NAME)
    @ConditionalOnMissingBean(name = REDISTACK_WAIT_STRATEGY_BEAN_NAME)
    @ConditionalOnProperty(name = "embedded.redistack.clustered", havingValue = "true")
    public WaitStrategy redistackClusterWaitStrategy(RedistackProperties properties) {
        return new DefaultRedistackClusterWaitStrategy(properties);
    }

    @SuppressWarnings("all")
    @Bean(name = RedistackProperties.BEAN_NAME_EMBEDDED_REDIS_STACK, destroyMethod = "stop")
    public GenericContainer redistack(
            ConfigurableEnvironment environment,
            @Qualifier(REDISTACK_WAIT_STRATEGY_BEAN_NAME) WaitStrategy redistackStartupCheckStrategy)
            throws IOException {
        GenericContainer redistack =
            new FixedHostPortGenericContainer(ContainerUtils.getDockerImageName(properties).asCanonicalNameString())
                // .withFixedExposedPort(properties.getPort(), 6379)
                // .withFixedExposedPort(properties.getHttpPort(), 8001)
                .withExposedPorts(properties.getPort(), properties.getHttpPort())
                .withCopyFileToContainer(MountableFile.forHostPath(prepareRedistackConf()),
                                        "/redis-stack.conf")
                .withCopyFileToContainer(MountableFile.forHostPath(prepareRedistackNodesConf()),
                                        "/nodes.conf")
                .waitingFor(redistackStartupCheckStrategy);
        // if (properties.isRequirepass()) {
        //     redistack.withEnv("REDIS_ARGS", "--requirepass passw");
        // }
        redistack = ContainerUtils.configureCommonsAndStart(redistack, properties, log);
        Map<String, Object> redistackEnv = EnvUtils.registerRedisEnvironment(environment, redistack, properties,
            redistack.getMappedPort(properties.getPort()), redistack.getMappedPort(properties.getHttpPort()));
        log.info("Started Redistack cluster. Connection details: {}", redistackEnv);
        return redistack;
    }

    private Path prepareRedistackConf() throws IOException {
        return FileUtils.resolveTemplateAsPath(
            resourceLoader,
            "redis.conf",
            content -> content
                .replace("{{requirepass}}", properties.isRequirepass() ? "yes" : "no")
                .replace("{{password}}",
                    properties.isRequirepass() ? "requirepass " + properties.getPassword() : "")
                .replace("{{clustered}}", properties.isClustered() ? "yes" : "no")
                .replace("{{port}}", String.valueOf(properties.getPort()))
        );
    }

    private Path prepareRedistackNodesConf() throws IOException {
        return FileUtils.resolveTemplateAsPath(
            resourceLoader,
            "nodes.conf",
            content -> content
                .replace("{{port}}", String.valueOf(properties.getPort()))
                .replace("{{busPort}}", String.valueOf(properties.getPort() + 10000))
        );
    }
}
