package io.micrc.core.rpc.springboot;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({ RpcMockServerScanner.class })
public class RpcAutoConfiguration {
    
}
