package io.micrc.testcontainers.kafdrop;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.testcontainers.containers.Network;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EmbeddedKafdropBootstrapConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Network.class)
    public Network kafkaNetwork() {
        // Network network = Network.newNetwork();
        log.info("hack: Disable created docker network for reuse container");
        return null;
    }
}
