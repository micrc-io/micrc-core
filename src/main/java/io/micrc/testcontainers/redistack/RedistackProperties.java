package io.micrc.testcontainers.redistack;

import java.util.Arrays;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.github.dockerjava.api.model.Capability;
import com.playtika.test.common.properties.CommonContainerProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * redis-stack for embedded-redis and redisinsight-ui
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-01 18:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties("embedded.redistack")
public class RedistackProperties extends CommonContainerProperties {
    public static final String BEAN_NAME_EMBEDDED_REDIS_STACK = "embeddedRedistack";

    private String password = "passw";
    private String host = "localhost";
    private int port = 6379;
    private int httpPort = 8001;
    private boolean clustered = true;
    private boolean requirepass = false;

    public RedistackProperties() {
        this.setCapabilities(Arrays.asList(Capability.NET_ADMIN));
    }

    @Override
    public String getDefaultDockerImage() {
        return "redis/redis-stack:6.2.4-v2";
    }
}
