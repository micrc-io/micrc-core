package io.micrc.core.message.springboot;

import io.micrc.core.AbstractRouteTemplateParamSource;
import io.micrc.core.annotations.application.businesses.BusinessesService;
import io.micrc.core.annotations.integration.MessageAdapter;
import io.micrc.core.message.EnableMessage;
import io.micrc.core.message.MessageRouteConfiguration;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息监听适配器扫描，注入路由参数bean，用于每个适配器生成路由
 *
 * @author weiguan
 * @date 2022-09-13 05:30
 * @since 0.0.1
 */
public class ClassPathMessageScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(EnableMessage.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata).getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }
        AbstractRouteTemplateParamSource source = new AbstractRouteTemplateParamSource();
        MessageSubscriberScanner messageSubscriberScanner = new MessageSubscriberScanner(registry, source);
        messageSubscriberScanner.setResourceLoader(resourceLoader);
        messageSubscriberScanner.doScan(basePackages);
        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition((Class<AbstractRouteTemplateParamSource>) source.getClass(), () -> source).getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(beanDefinition, registry), beanDefinition);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}

class MessagePublisherScanner extends ClassPathBeanDefinitionScanner {

    private final AbstractRouteTemplateParamSource sourceDefinition;

    public MessagePublisherScanner(BeanDefinitionRegistry registry, AbstractRouteTemplateParamSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(BusinessesService.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            // TODO 这里构造全局EventInfo
        }
        holders.clear();
        return holders;
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry routersInfo) {
        // nothing to do. leave it out.
    }
}

class MessageSubscriberScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final AbstractRouteTemplateParamSource sourceDefinition;

    public MessageSubscriberScanner(BeanDefinitionRegistry registry, AbstractRouteTemplateParamSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(MessageAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());

            sourceDefinition.addParameter(
                    routeId(""),
                    MessageRouteConfiguration.MessageDefinition.builder()
                            .templateId(MessageRouteConfiguration.ROUTE_TMPL_MESSAGE_PUBLISHER)
                            .build()
            );
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
        return MessageRouteConfiguration.ROUTE_TMPL_MESSAGE_PUBLISHER + "-" + routeId;
    }
}