package io.micrc.testcontainers.redistack.wait;

import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import io.micrc.testcontainers.redistack.RedistackProperties;

/**
 * DefaultRedistackClusterWaitStrategy
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-02 02:01
 */
public class DefaultRedistackClusterWaitStrategy extends WaitAllStrategy implements RedistackClusterWaitStrategy {
    public DefaultRedistackClusterWaitStrategy(RedistackProperties properties) {
        withStrategy(new RedistackStatusCheck(properties))
            .withStrategy(new RedistackClusterStatusCheck(properties));
    }
}
