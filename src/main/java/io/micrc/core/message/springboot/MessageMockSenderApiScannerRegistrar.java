package io.micrc.core.message.springboot;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;

import io.micrc.core.EnableMicrcSupport;
import lombok.extern.slf4j.Slf4j;

/**
 * message监听在default，local环境下的mock api，构建rest api发送消息，以触发监听
 * 扫描所有消息监听注解，生成rest api以模拟发送监听的消息
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-08 21:45
 */
@Slf4j
public class MessageMockSenderApiScannerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

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
        log.error("扫描所有消息监听，创建mock rest api，触发消息发送，使监听器工作以调试");
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
    
}
