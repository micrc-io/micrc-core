package io.micrc.core.rpc.springboot;

import io.micrc.core.rpc.IntegrationsInfo;
import io.micrc.lib.JsonUtil;
import org.apache.camel.component.direct.DirectComponent;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.OpenAPIExpectation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock Server
 */
@Configuration
public class RpcMockServerAutoConfiguration {

    @Autowired
    private IntegrationsInfo integrationsInfo;

    @Bean("rest-openapi-executor")
    public DirectComponent restOpenapiExecutor() {
        return new DirectComponent();
    }

    @Profile({"default", "local"})
    @Bean
    public ClientAndServer clientAndServer() {
        ClientAndServer server = ClientAndServer.startClientAndServer(1080);
        integrationsInfo.getAll().forEach(integration -> {
            try {
                Resource resource = new PathMatchingResourcePatternResolver()
                        .getResource(ResourceUtils.CLASSPATH_URL_PREFIX + integration.getProtocolFilePath());
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
                String filePath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + "mockserver/" + integration.getProtocolFilePath();
                saveStringToFile(filePath , openApiBodyContent);
                OpenAPIExpectation openAPIExpectation = OpenAPIExpectation.openAPIExpectation(filePath);
                server.upsert(openAPIExpectation);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return server;
    }

    private void saveStringToFile(String filePath, String content) {
        byte[] sourceByte = content.getBytes();
        if (null != sourceByte) {
            try {
                File file = new File(filePath);        //文件路径（路径+文件名）
                if(file.exists() && !file.isDirectory()){
                    file.deleteOnExit();
                }
                if (!file.exists()) {    //文件不存在则创建文件，先创建目录
                    File dir = new File(file.getParent());
                    dir.mkdirs();
                    file.createNewFile();
                }
                FileOutputStream outStream = new FileOutputStream(file);    //文件输出流用于将数据写入文件
                outStream.write(sourceByte);
                outStream.close();    //关闭文件输出流
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
