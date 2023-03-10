package io.micrc.core.rpc.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.application.businesses.BusinessesService;
import io.micrc.core.annotations.application.businesses.DeriveIntegration;
import io.micrc.core.annotations.application.presentations.PresentationsService;
import io.micrc.core.rpc.IntegrationsInfo;
import io.micrc.core.rpc.IntegrationsInfo.Integration;
import io.micrc.lib.FileUtils;
import io.micrc.lib.JsonUtil;
import lombok.SneakyThrows;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扫描所有集成请求的注解，获取其openapi地址 用于衍生服务调用时协议交换
 *
 * @author weiguan
 * @date 2022-09-08 21:45
 * @since 0.0.1
 */
public class IntegrationInfoScannerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment env;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
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

        IntegrationsInfo integrationsInfo = new IntegrationsInfo();
        new RPCRequestScanner(registry, integrationsInfo).doScan(basePackages);
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

        public RPCRequestScanner(BeanDefinitionRegistry registry, IntegrationsInfo integrationsInfo) {
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
            holders.clear();
            return holders;
        }

        @Override
        protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry routersInfo) {
            // nothing to do. leave it out.
        }

        @SneakyThrows
        private Integration analysisOpenApiProtocol(String protocolFilePath) {
            String protocolContent = FileUtils.fileReader(protocolFilePath, List.of("json"));
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
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}

