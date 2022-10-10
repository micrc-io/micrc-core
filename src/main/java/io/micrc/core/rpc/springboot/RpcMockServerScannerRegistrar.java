package io.micrc.core.rpc.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.application.businesses.BusinessesService;
import io.micrc.core.annotations.application.businesses.DeriveIntegration;
import io.micrc.core.annotations.application.presentations.PresentationsService;
import io.micrc.core.rpc.IntegrationsInfo;
import io.micrc.core.rpc.IntegrationsInfo.Integration;
import io.micrc.lib.JsonUtil;
import lombok.SneakyThrows;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * rpc调用在default，local环境下的mock服务端
 * 扫描所有rpc集成请求的注解，获取其openapi spec，以创建Expectation用于校验request并fake response
 *
 * @author weiguan
 * @date 2022-09-08 21:45
 * @since 0.0.1
 */
public class RpcMockServerScannerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment env;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        Optional<String> profileStr = Optional.ofNullable(env.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        if (!profiles.contains("local") && !profiles.contains("default")) {
            return;
        }
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableMicrcSupport.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("basePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        ClientAndServer server = ClientAndServer.startClientAndServer(1080); // 启动mock server
        IntegrationsInfo integrationsInfo = new IntegrationsInfo();
        new RPCRequestScanner(registry, server, integrationsInfo).doScan(basePackages);
        // 以mockServer为beanName，将server注册进去
        @SuppressWarnings("unchecked")
        BeanDefinition clientAndServerBeanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition((Class<ClientAndServer>) server.getClass(), () -> server)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(clientAndServerBeanDefinition, registry),
                clientAndServerBeanDefinition);
        // 注册全局集成信息
        @SuppressWarnings("unchecked")
        BeanDefinition integrationsInfoBeanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition((Class<IntegrationsInfo>) integrationsInfo.getClass(), () -> integrationsInfo)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(integrationsInfoBeanDefinition, registry),
                integrationsInfoBeanDefinition);
    }

    private static class RPCRequestScanner extends ClassPathBeanDefinitionScanner {

        private final IntegrationsInfo integrationsInfo;

        public RPCRequestScanner(BeanDefinitionRegistry registry, ClientAndServer server, IntegrationsInfo integrationsInfo) {
            super(registry);
            this.integrationsInfo = integrationsInfo;
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            AnnotationMetadata metadata = beanDefinition.getMetadata();
            return metadata.isInterface() && metadata.isIndependent();
        }

        @SneakyThrows
        @Override
        protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
            this.addIncludeFilter(new AnnotationTypeFilter(BusinessesService.class)); // 业务逻辑的集成
            this.addIncludeFilter(new AnnotationTypeFilter(PresentationsService.class)); // 展示逻辑的集成
            // 衍生逻辑: 衍生不可再衍生,无rpc集成
            Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
            for (BeanDefinitionHolder holder : holders) {
                GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
                beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
                BusinessesService businessesService = beanDefinition.getBeanClass().getAnnotation(BusinessesService.class);
                if (null != businessesService) {
                    Method[] methods = beanDefinition.getBeanClass().getMethods();
                    List<Method> executeMethods = Arrays.stream(methods).filter(method -> method.getName().equals("execute") && !method.isBridge()).collect(Collectors.toList());
                    if (executeMethods.size() != 1) {
                        throw new RuntimeException("the " + beanDefinition.getBeanClass().getName() + " don`t have only one execute method, please check this application service....");
                    }
                    Parameter[] parameters = executeMethods.get(0).getParameters();
                    if (parameters.length != 1) {
                        throw new RuntimeException("the " + beanDefinition.getBeanClass().getName() + " execute method don`t have only one command param, please check this application service....");
                    }
                    Field[] commandFields = parameters[0].getType().getDeclaredFields();
                    // 获取Command身上的参数的服务集成注解
                    Arrays.stream(commandFields).forEach(field -> {
                        DeriveIntegration deriveIntegration = field.getAnnotation(DeriveIntegration.class);
                        if (null != deriveIntegration) {
                            integrationsInfo.add(this.analysisOpenApiProtocol(deriveIntegration.protocolPath()));
                        }
                    });
                }
                PresentationsService presentationsService = beanDefinition.getBeanClass().getAnnotation(PresentationsService.class);
                if (null != presentationsService) {
                    Arrays.stream(presentationsService.integrations()).forEach(integration -> {
                        integrationsInfo.add(this.analysisOpenApiProtocol(integration.protocol()));
                    });
                }
            }
            // MonkServer注册协议
//            integrationsInfo.getAll().stream().forEach(integration -> {
//                server.upsert(OpenAPIExpectation.openAPIExpectation(integration.getProtocolFilePath()));
//            });
            holders.clear();
            return holders;
        }

        @Override
        protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry routersInfo) {
            // nothing to do. leave it out.
        }

        @SneakyThrows
        private Integration analysisOpenApiProtocol(String protocolFilePath) {
            String protocolContent = this.fileReader(protocolFilePath);
            JsonNode protocolNode = JsonUtil.readTree(protocolContent);
            // 收集host
            JsonNode hostNode = protocolNode
                    .at("/servers").get(0)
                    .at("/url");
            // 收集operationId
            JsonNode operationNode = protocolNode
                    .at("/paths").elements().next().elements().next()
                    .at("/operationId");
            return Integration.builder()
                    .protocolFilePath(protocolFilePath)
                    .operationId(operationNode.textValue())
                    .host(hostNode.textValue())
                    .build();
        }

        private String fileReader(String filePath) throws FileNotFoundException {
            StringBuffer fileContent = new StringBuffer();
            try {
                InputStream stream = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(filePath);
                BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                String str = null;
                while ((str = in.readLine()) != null) {
                    fileContent.append(str);
                }
                in.close();
            } catch (IOException e) {
                throw new FileNotFoundException("the openapi protocol file not found...");
            }
            return fileContent.toString();
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}

