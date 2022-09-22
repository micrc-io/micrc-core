package io.micrc.core.rpc.springboot;

import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.RpcRestRouteConfiguration;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.rest.RestComponent;
import org.apache.camel.component.rest.openapi.springboot.RestOpenApiComponentAutoConfiguration;
import org.apache.camel.component.rest.springboot.RestComponentAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import java.util.Map;

/**
 * rpc auto configuration，注册rpc路由组件，配置camel rest dsl
 *
 * @author weiguan
 * @date 2022-09-08 21:45
 * @since 0.0.1
 */
@Configuration
@EnableAutoConfiguration(
        exclude = {RestOpenApiComponentAutoConfiguration.class, RestComponentAutoConfiguration.class}
)
@Import({RpcRestRouteConfiguration.class})
public class RpcAutoConfiguration {

    @Bean("req")
    public DirectComponent req() {
        DirectComponent request = new DirectComponent();
        return request;
    }


    @Bean("rest")
    public RestComponent rest() {
        RestComponent response = new RestComponent();
        response.setBridgeErrorHandler(true);
        return response;
    }

    @Bean
    public RoutesBuilder restConfiguration() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("servlet");
            }
        };
    }

    @Profile({"default", "local"})
    @Bean
    public RoutesBuilder rpcApiDocumentEndpoint() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("apiDocRest")
                        .templateParameter("apidoc-path")
                        .templateParameter("apidoc")
                        .from("rest:get:{{apidoc-path}}")
                        .setBody().constant("{{apidoc}}")
                        .end();

                for (Map.Entry<String, String> entry : RpcEnvironmentProcessor.APIDOCS.entrySet()) {
                    templatedRoute("apiDocRest")
                            .routeId(entry.getKey())
                            .parameter("apidoc-path", entry.getKey())
                            .parameter("apidoc", entry.getValue());
                }
            }
        };
    }

    @Bean
    public RoutesBuilder restDemo() {
        return new MicrcRouteBuilder() {
            @Override
            public void configureRoute() throws Exception {
                from("direct:test").log("rest test done.").setBody().constant("{}");
                from("timer:restDemo?delay=10000&repeatCount=1")
                        .log("starting restDemo...")
                        // .setBody().constant("test body")
                        .to("rest:get:/api/test?host=localhost:8080");

                routeTemplate("restTestTemplate")
                        .templateParameter("verb")
                        .templateParameter("api-path")
                        .from("rest:{{verb}}:{{api-path}}")
                        .to("direct:test");

                templatedRoute("restTestTemplate")
                        .parameter("verb", "get")
                        .parameter("api-path", "test");
            }
        };
    }
}
