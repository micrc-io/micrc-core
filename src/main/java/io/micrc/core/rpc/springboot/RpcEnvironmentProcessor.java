package io.micrc.core.rpc.springboot;

import io.micrc.lib.JsonUtil;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * rpc env config. swagger-ui api doc path
 *
 * @author weiguan
 * @date 2022-09-12 03:48
 * @since 0.0.1
 */
public class RpcEnvironmentProcessor implements EnvironmentPostProcessor {

    static final Map<String, String> APIDOCS = new HashMap<>();

    private static final String RPC_ENV_NAME = "micrc-rpc";
    private static final String REST_CONTEXT_PATH = "/api";
    private static final String REST_CONTEXT_PATH_PATTERN = REST_CONTEXT_PATH + "/*";
    private static final String APIDOC_BASE_PATH = "apidoc";
    private static final String APIDOC_BASE_URI = "/" + APIDOC_BASE_PATH + "/";

    private final Log log;

    public RpcEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        Properties properties = new Properties();
        properties.setProperty("camel.servlet.mapping.contextPath", REST_CONTEXT_PATH_PATTERN);
        log.info("Rest Endpoint On Port: 8080 With Context Path: " + REST_CONTEXT_PATH);
        if (!profiles.contains("default") && !profiles.contains("local")) {
            log.info("Not default or local Environment, Disable Swagger-UI. ");
            properties.setProperty("springdoc.api-docs.enabled", "false");
        }
        if (profiles.contains("default") || profiles.contains("local")) {
            envForApidoc(properties);
        }
        envForMockServer(properties);

        PropertiesPropertySource source = new PropertiesPropertySource(RPC_ENV_NAME, properties);
        environment.getPropertySources().addLast(source);
    }

    private void envForMockServer(Properties properties) {
        properties.setProperty("logging.level.org.mockserver", "WARN");
    }

    private void envForApidoc(Properties properties) {
        Resource base = new ClassPathResource(APIDOC_BASE_PATH);
        if (!base.exists()) {
            throw new IllegalStateException(
                    "'" + APIDOC_BASE_PATH + "'" + " for swagger-ui in classpath must be exists.");
        }
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(ResourceUtils.CLASSPATH_URL_PREFIX + APIDOC_BASE_PATH + "/**/*.json");
            // swagger config  read https://springdoc.org/v2/
            for (int i = 0; i < resources.length; i++) {
                Resource resource = resources[i];
                String apidocUrl = base.getURI().relativize(resource.getURI()).getPath()
                        .replace(".json", "");
                String openApiBodyContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                Map<String, Object> pathsMap = JsonUtil.writeObjectAsObject(JsonUtil.readTree(openApiBodyContent).at("/paths"), HashMap.class);
                Map<String, Object> apiPathsMap = new HashMap<>();
                pathsMap.keySet().forEach(key -> {
                    if (!key.contains("/api/")) {
                        apiPathsMap.put("/api" + key, pathsMap.get(key));
                    }
                    if (key.contains("/api/")) {
                        apiPathsMap.put(key, pathsMap.get(key));
                    }
                });
                openApiBodyContent = JsonUtil.patch(openApiBodyContent, "/paths", JsonUtil.writeValueAsStringRetainNull(apiPathsMap));
                APIDOCS.put(APIDOC_BASE_URI + apidocUrl,
                        openApiBodyContent);
                properties.setProperty("springdoc.swagger-ui.urls[" + i + "].url",
                        REST_CONTEXT_PATH + APIDOC_BASE_URI + apidocUrl);
                properties.setProperty("springdoc.swagger-ui.urls[" + i + "].name", apidocUrl);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable load api doc file from '" + APIDOC_BASE_PATH + "'", e);
        }
    }
}
