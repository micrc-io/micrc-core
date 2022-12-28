package io.micrc.core.rpc.springboot;

import io.micrc.core.rpc.IntegrationsInfo;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mock Server
 */
@Configuration
public class RpcMockServerAutoConfiguration {

    @Autowired(required = false)
    private IntegrationsInfo integrationsInfo;

    @Bean("rest-openapi-executor")
    public DirectComponent restOpenapiExecutor() {
        return new DirectComponent();
    }

//    @Profile({"default", "local"})
//    @Bean
//    public ClientAndServer clientAndServer() {
//        ClientAndServer server = ClientAndServer.startClientAndServer(1080);
//        integrationsInfo.getAll().forEach(integration -> {
//            OpenAPIExpectation openAPIExpectation = OpenAPIExpectation.openAPIExpectation(integration.getProtocolFilePath());
//            server.upsert(openAPIExpectation);
//        });
//        return server;
//    }
}
