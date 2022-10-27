package io.micrc.core.rpc.springboot;

import io.micrc.core.rpc.IntegrationsInfo;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.OpenAPIExpectation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Mock Server
 */
@Configuration
public class RpcMockServerAutoConfiguration {

    @Autowired
    private IntegrationsInfo integrationsInfo;

    @Profile({"default", "local"})
    @Bean
    public ClientAndServer clientAndServer() {
        ClientAndServer server = ClientAndServer.startClientAndServer(1080);
        integrationsInfo.getAll().forEach(integration -> {
            OpenAPIExpectation openAPIExpectation = OpenAPIExpectation.openAPIExpectation(integration.getProtocolFilePath());
            server.upsert(openAPIExpectation);
        });
        return server;
    }
}
