package io.micrc.testcontainers.redistack;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;

import com.playtika.test.common.operations.DefaultNetworkTestOperations;
import com.playtika.test.common.operations.NetworkTestOperations;
import com.playtika.test.common.properties.InstallPackageProperties;
import com.playtika.test.common.utils.ApkPackageInstaller;
import com.playtika.test.common.utils.PackageInstaller;

/**
 * test operations
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-01 21:10
 */
@Configuration
@ConditionalOnBean(RedistackProperties.class)
@ConditionalOnExpression("${embedded.containers.enabled:true}")
@ConditionalOnProperty(name = "embedded.redistack.enabled", matchIfMissing = true)
@SuppressWarnings("all")
public class EmbeddedRedistackTestOperationsAutoConfiguration {

    @Bean
    @ConfigurationProperties("embedded.redistack.install")
    public InstallPackageProperties redistackPackageProperties() {
        InstallPackageProperties properties = new InstallPackageProperties();
        properties.setPackages(Collections.singleton("iproute2"));
        return properties;
    }

    @Bean
    public PackageInstaller redistackPackageInstaller(
            InstallPackageProperties properties,
            @Qualifier(RedistackProperties.BEAN_NAME_EMBEDDED_REDIS_STACK) GenericContainer redistack) {
        return new ApkPackageInstaller(properties, redistack);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redistackNetworkTestOperations")
    public NetworkTestOperations redistackNetworkTestOperations(
            @Qualifier(RedistackProperties.BEAN_NAME_EMBEDDED_REDIS_STACK) GenericContainer redistack) {
        return new DefaultNetworkTestOperations(redistack);
    }
}
