package io.micrc.testcontainers.redistack.wait;

import com.playtika.test.common.checks.AbstractCommandWaitStrategy;

import io.micrc.testcontainers.redistack.RedistackProperties;
import lombok.RequiredArgsConstructor;

/**
 * redis status check
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-02 00:13
 */
@RequiredArgsConstructor
public class RedistackStatusCheck extends AbstractCommandWaitStrategy {
    private final RedistackProperties properties;

    @Override
    public String[] getCheckCommand() {
        if (properties.isRequirepass()) {
            return new String[] {
                "redis-cli",
                "-a", properties.getPassword(),
                "-p", String.valueOf(properties.getPort()),
                "--no-auth-warning",
                "ping"
            };
        } else {
            return new String[] {
                "redis-cli",
                "-p", String.valueOf(properties.getPort()),
                "ping"
            };
        }
    }
}
