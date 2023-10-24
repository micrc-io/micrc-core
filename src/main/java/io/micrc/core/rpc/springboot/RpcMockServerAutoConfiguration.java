package io.micrc.core.rpc.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrc.core.rpc.IntegrationsInfo;
import io.micrc.core.rpc.RpcRestRouteConfiguration;
import io.micrc.lib.JsonUtil;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock Server
 */
@Configuration
public class RpcMockServerAutoConfiguration {

    @Profile({"default", "local"})
    @Bean
    public ClientAndServer clientAndServer() {
        ClientAndServer server = ClientAndServer.startClientAndServer(1080);
        IntegrationsInfo.getAll().stream().map(integration -> {
            String openApiBodyContent = integration.getProtocolContent();
            JsonNode protocolNode = JsonUtil.readTree(openApiBodyContent);
            JsonNode serverNode = protocolNode.at("/servers").get(0);
            String serverJson = JsonUtil.writeValueAsString(serverNode);
            String url = serverNode.at("/url").textValue();
            if (!url.startsWith("/api/")) {
                url = "/api" + url;
            }
            serverJson = JsonUtil.patch(serverJson, "/url", JsonUtil.writeValueAsString(url));
            List<HashMap> servers = List.of(JsonUtil.writeValueAsObject(serverJson, HashMap.class));
            openApiBodyContent = JsonUtil.patch(openApiBodyContent, "/servers", JsonUtil.writeValueAsString(servers));
            String filePath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + "mockserver/" + integration.getProtocolFilePath();
            saveStringToFile(filePath , openApiBodyContent);
            // 临时文件的集成信息
            return IntegrationsInfo.Integration.builder()
                    .operationId(integration.getOperationId())
                    .xHost(integration.getXHost())
                    .protocolFilePath(filePath)
                    .url(url)
                    .protocolContent(openApiBodyContent)
                    .build();
        }).collect(Collectors.groupingBy(i -> i.getUrl() + "/" + i.getOperationId()))
                .forEach((urlPath, apiContents) -> {
                    if (apiContents.size() == 1) {
                        // 直接MOCK
                        IntegrationsInfo.Integration integration = apiContents.get(0);
                        OpenAPIExpectation openAPIExpectation = OpenAPIExpectation.openAPIExpectation(integration.getProtocolFilePath());
                        server.upsert(openAPIExpectation);
                    } else {
                        // 需合并后MOCK
                        apiContents.stream()
                                .collect(Collectors.toMap(
                                        p -> p.getProtocolFilePath().split("mockserver/")[1],
                                        p -> JsonUtil.readTree(p.getProtocolContent()).at("/paths")
                                                .iterator().next().at("/post").at("/responses")
                                                .iterator().next().at("/content"),
                                        (p1, p2) -> p2)
                                ).forEach((filePath, responseContent) -> {
                                    Header filePathHeader = Header.header(RpcRestRouteConfiguration._FILE_PATH, filePath);
                                    HttpRequest httpRequest = HttpRequest.request().withPath(urlPath).withHeader(filePathHeader);
                                    String response = JsonUtil.writeValueAsString(responseContent.iterator().next()
                                            .at("/examples").iterator().next().at("/value"));
                                    Header contentTypeHeader = Header.header(HttpHeaders.CONTENT_TYPE, responseContent.fields().next().getKey());
                                    server.when(httpRequest).respond(HttpResponse.response(response).withHeader(contentTypeHeader));
                                });
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