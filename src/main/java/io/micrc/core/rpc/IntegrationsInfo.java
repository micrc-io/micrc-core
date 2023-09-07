package io.micrc.core.rpc;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.*;

/**
 * TODO
 *
 * @author tengwang
 * @date 2022/9/22 15:17
 * @since 0.0.1
 */
@NoArgsConstructor
public class IntegrationsInfo {

    private static final Map<String, Integration> caches = new HashMap<>();

    private static List<Integration> integrationsInfo = new ArrayList<>();

    public void add(Integration integration) {
        integrationsInfo.add(integration);
    }

    public static Integration get(String protocolFilePath) {
        Integration integration = caches.get(protocolFilePath);
        if (null == integration) {
            Optional<Integration> integrate = integrationsInfo.stream().filter(integrateInfo -> protocolFilePath.equals(integrateInfo.protocolFilePath)).findFirst();
            caches.put(integrate.get().getProtocolFilePath(), integrate.get());
        }
        return caches.get(protocolFilePath);
    }

    public static List<Integration> getAll() {
        return integrationsInfo;
    }

    @Data
    @SuperBuilder
    public static class Integration {

        /**
         * 协议文件地址
         */
        private String protocolFilePath;

        /**
         * 操作ID
         */
        private String operationId;

        /**
         * URL
         */
        private String url;

        /**
         * 自定义主机
         */
        private String xHost;

        /**
         * 协议内容
         */
        private String protocolContent;
    }
}