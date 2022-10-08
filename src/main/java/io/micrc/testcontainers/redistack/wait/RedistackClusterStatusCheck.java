package io.micrc.testcontainers.redistack.wait;

import java.util.Arrays;

import org.springframework.util.StringUtils;
import org.testcontainers.containers.Container;

import com.playtika.test.common.checks.AbstractRetryingWaitStrategy;
import com.playtika.test.common.utils.ContainerUtils;

import io.micrc.testcontainers.redistack.RedistackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * redis cluster status check
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-02 01:56
 */
@Slf4j
@RequiredArgsConstructor
public class RedistackClusterStatusCheck extends AbstractRetryingWaitStrategy {
    private final RedistackProperties properties;

    @Override
    protected boolean isReady() {
        String commandName = getContainerType();
        String containerId = waitStrategyTarget.getContainerId();
        String[] checkCommand = getCheckCommand();
        log.debug("{} execution of command {} for container id: {} ",
            commandName, Arrays.toString(checkCommand), containerId);

        Container.ExecResult healthCheckCmdResult = ContainerUtils.executeInContainer(waitStrategyTarget, checkCommand);
        log.debug("{} executed with result: {}", commandName, healthCheckCmdResult);

        if (healthCheckCmdResult.getExitCode() != 0
            || StringUtils.hasText(healthCheckCmdResult.getStderr())
            || healthCheckCmdResult.getStdout().contains("cluster_state:fail")) {
            log.debug("{} executed with exitCode !=0, considering status as unknown", commandName);
            return false;
        }
        log.debug("{} command executed, considering container {} successfully started", commandName, containerId);
        return true;
    }

    protected String[] getCheckCommand() {
        if (properties.isRequirepass()) {
            return new String[] {
                "redis-cli",
                "-a", properties.getPassword(),
                "-p", String.valueOf(properties.getPort()),
                "--no-auth-warning",
                "cluster", "info"
            };
        } else {
            return new String[] {
                "redis-cli",
                "-p", String.valueOf(properties.getPort()),
                "cluster", "info"
            };
        }
    }
}
