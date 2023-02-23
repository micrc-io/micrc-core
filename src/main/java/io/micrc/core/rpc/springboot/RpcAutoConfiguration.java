package io.micrc.core.rpc.springboot;

import io.micrc.core.rpc.RpcRestRouteConfiguration;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.rest.RestComponent;
import org.apache.camel.component.rest.openapi.RestOpenApiComponent;
import org.apache.camel.component.rest.openapi.springboot.RestOpenApiComponentAutoConfiguration;
import org.apache.camel.component.rest.springboot.RestComponentAutoConfiguration;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
        exclude = {
                RestOpenApiComponentAutoConfiguration.class,
                RestComponentAutoConfiguration.class
        }
)
@Import(
        {
                RpcRestRouteConfiguration.class,
        }
)
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

    @Bean("rest-openapi-ssl")
    public RestOpenApiComponent restOpenapiSSlComponent() {
        RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName("undertow");
        // 设置信任全部证书
        SSLContextParameters parameters = new SSLContextParameters();
        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setTrustManager(new TrustALLManager());
        parameters.setTrustManagers(trustManagersParameters);
        component.setSslContextParameters(parameters);
        // 桥接错误
        component.setBridgeErrorHandler(true);
        return component;
    }

    @Bean("rest-openapi-derive")
    public RestOpenApiComponent restOpenapiDeriveComponent() {
        RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName("undertow");
        component.setBasePath("/api");
        // 桥接错误
        component.setBridgeErrorHandler(true);
        return component;
    }

    private class TrustALLManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Do nothing
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Do nothing
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
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
}
