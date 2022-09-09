package io.micrc.core.rpc.springboot;

import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.application.businesses.BusinessesService;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
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
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RpcMockServerScanner implements ImportBeanDefinitionRegistrar, EnvironmentAware {

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
        new RPCRequestScanner(registry).doScan(basePackages);
    }

    private static class RPCRequestScanner extends ClassPathBeanDefinitionScanner {

        public RPCRequestScanner(BeanDefinitionRegistry registry) {
            super(registry);
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            AnnotationMetadata metadata = beanDefinition.getMetadata();
            return metadata.isInterface() && metadata.isIndependent();
        }

        @Override
        protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
            this.addIncludeFilter(new AnnotationTypeFilter(BusinessesService.class)); // 业务逻辑的集成
            // this.addIncludeFilter(new AnnotationTypeFilter(PresentationsService.class)); // 展示逻辑的集成
            // this.addIncludeFilter(new AnnotationTypeFilter(annotationType)); // 衍生逻辑的集成
            Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
            ClientAndServer server = ClientAndServer.startClientAndServer(1080); // 启动mock server
            for (BeanDefinitionHolder holder : holders) {
                GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
                // TODO 业务逻辑: 获取command的属性上的集成注解，得到openapi协议classpath路径specPath
                // TODO server.upsert(OpenAPIExpectation.openAPIExpectation(specPath));
                // TODO 展示逻辑: 获取注解上的集成中的openapi协议classpath路径
                // TODO server.upsert(OpenAPIExpectation.openAPIExpectation(specPath));
                // TODO 衍生逻辑: 获取注解上的集成中的openapi协议classpath路径
                // TODO server.upsert(OpenAPIExpectation.openAPIExpectation(specPath));
            }
            holders.clear();
            // TODO 可以以mockServer为beanName，将server注册进去
            return holders;
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
