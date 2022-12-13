package io.micrc.core.message.rabbit.springboot;

import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.message.rabbit.RabbitMessageAdapter;
import io.micrc.core.message.rabbit.RabbitMessageMockSenderRouteConfiguration;
import io.micrc.core.message.rabbit.RabbitMessageMockSenderRouteTemplateParameterSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * message监听在default，local环境下的mock api，构建rest api发送消息，以触发监听
 * 扫描所有消息监听注解，生成rest api以模拟发送监听的消息
 *
 * @author weiguan
 * @date 2022-09-08 21:45
 * @since 0.0.1
 */
@Slf4j
public class RabbitMessageMockSenderApiScannerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment env;

    private ResourceLoader resourceLoader;

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
        RabbitMessageMockSenderRouteTemplateParameterSource source =
                new RabbitMessageMockSenderRouteTemplateParameterSource();

        // message mock sender scanner
        RabbitMessageMockSenderApiScanner messageMockSenderApiScanner =
                new RabbitMessageMockSenderApiScanner(registry, source);
        messageMockSenderApiScanner.setResourceLoader(resourceLoader);
        messageMockSenderApiScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<RabbitMessageMockSenderRouteTemplateParameterSource>) source.getClass(),
                        () -> source)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(beanDefinition, registry),
                beanDefinition);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

}

/**
 * 消息MOCK发送器扫描器，用于扫描@RabbitMessageAdapter注解
 * 获取注解中的声明逻辑的属性，构造路由模版源，最终注入camel context用于构造执行路由
 *
 * @author hyosunghan
 * @date 2022-11-19 14:16
 * @since 0.0.1
 */
class RabbitMessageMockSenderApiScanner extends ClassPathBeanDefinitionScanner {

    private static final AtomicInteger INDEX = new AtomicInteger();
    private final RabbitMessageMockSenderRouteTemplateParameterSource sourceDefinition;

    public RabbitMessageMockSenderApiScanner(BeanDefinitionRegistry registry,
                                                RabbitMessageMockSenderRouteTemplateParameterSource source) {
        super(registry, false);
        this.sourceDefinition = source;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isInterface() && metadata.isIndependent();
    }

    @SneakyThrows
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        this.addIncludeFilter(new AnnotationTypeFilter(RabbitMessageAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            RabbitMessageAdapter messageAdapter = beanDefinition.getBeanClass().getAnnotation(RabbitMessageAdapter.class);
            // 获取RabbitMessageAdapter注解参数
            String listenerName = beanDefinition.getBeanClass().getSimpleName();
            RabbitMessageMockSenderRouteConfiguration.MessageMockSenderDefinition build = RabbitMessageMockSenderRouteConfiguration.MessageMockSenderDefinition.builder()
                    .templateId(RabbitMessageMockSenderRouteConfiguration.ROUTE_TMPL_MESSAGE_SENDER)
                    .listenerName(listenerName)
                    .eventName(messageAdapter.eventName())
                    .build();
            sourceDefinition.addParameter(routeId(listenerName),build);
        }
        holders.clear();
        return holders;
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry routersInfo) {
        // nothing to do. leave it out.
    }

    private String routeId(String id) {
        String routeId = id;
        if (!StringUtils.hasText(routeId)) {
            routeId = String.valueOf(INDEX.getAndIncrement());
        }
        return RabbitMessageMockSenderRouteConfiguration.ROUTE_TMPL_MESSAGE_SENDER + "-" + routeId;
    }
}
