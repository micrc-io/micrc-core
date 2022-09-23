package io.micrc.core.message.springboot;

import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.application.businesses.DomainEvents;
import io.micrc.core.annotations.integration.message.MessageAdapter;
import io.micrc.core.message.MessageRouteConfiguration;
import io.micrc.core.message.MessageRouteConfiguration.EventsInfo;
import io.micrc.core.message.MessageRouteConfiguration.EventsInfo.Event;
import io.micrc.core.message.MessageSubscriberRouteParamSource;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import java.util.Arrays;
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
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(EnableMicrcSupport.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("basePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata).getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        /**
         * 发送器注解扫描
         */
        MessagePublisherScanner messagePublisherScanner = new MessagePublisherScanner(registry);
        messagePublisherScanner.setResourceLoader(resourceLoader);
        messagePublisherScanner.doScan(basePackages);

        /**
         * 接收器注解扫描
         */
        MessageSubscriberRouteParamSource source = new MessageSubscriberRouteParamSource();
        MessageSubscriberScanner messageSubscriberScanner = new MessageSubscriberScanner(registry, source);
        messageSubscriberScanner.setResourceLoader(resourceLoader);
        messageSubscriberScanner.doScan(basePackages);
        // registering
        @SuppressWarnings("unchecked")
        BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition((Class<MessageSubscriberRouteParamSource>) source.getClass(), () -> source).getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(beanDefinition, registry), beanDefinition);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}

class MessagePublisherScanner extends ClassPathBeanDefinitionScanner {

    public MessagePublisherScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isInterface() && metadata.isIndependent();
    }

    @SneakyThrows
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        this.addIncludeFilter(new AnnotationTypeFilter(DomainEvents.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        EventsInfo eventsInfo = new EventsInfo();
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            // 构造全局EventsInfo
            DomainEvents domainEvents = beanDefinition.getBeanClass().getAnnotation(DomainEvents.class);
            Arrays.stream(domainEvents.events()).forEach(eventInfo -> {
                String channel = eventInfo.eventName() + "-" + eventInfo.channel();
                Event event = Event.builder()
                        .eventName(eventInfo.eventName())
                        .exchangeName(eventInfo.aggregationName())
                        .channel(channel)
                        .mappingPath(eventInfo.mappingPath())
                        .ordered(eventInfo.ordered())
                        .build();
                eventsInfo.put(channel, event);
            });
        }
        this.registBean(eventsInfo);
        holders.clear();
        return holders;
    }

    private void registBean(Object beanInstance) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanInstance);
        beanDefinition.setBeanClass(EventsInfo.class);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        beanDefinition.setLazyInit(false);
        beanDefinition.setPrimary(true);
        beanDefinition.setScope("singleton");
        String beanName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(beanDefinition, super.getRegistry());
        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
        super.registerBeanDefinition(definitionHolder, super.getRegistry());
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry beanDefinitionRegistry) {
        // nothing to do.
    }
}

class MessageSubscriberScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final MessageSubscriberRouteParamSource sourceDefinition;

    public MessageSubscriberScanner(BeanDefinitionRegistry registry, MessageSubscriberRouteParamSource source) {
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
            MessageAdapter messageAdapterAnnotation = beanDefinition.getBeanClass().getAnnotation(MessageAdapter.class);
            if (messageAdapterAnnotation.custom()) {
                continue;
            }
            String adapterName = beanDefinition.getBeanClass().getSimpleName();
            String exchangeName = messageAdapterAnnotation.exchangeName();
            String eventName = messageAdapterAnnotation.eventName();
            String logicName = messageAdapterAnnotation.logicName();
            Boolean ordered = messageAdapterAnnotation.ordered();
            sourceDefinition.addParameter(
                    routeId(exchangeName + "-" + eventName + "-" + logicName + "-" + "EventListener"),
                    MessageRouteConfiguration.MessageDefinition.builder()
                            .templateId(MessageRouteConfiguration.ROUTE_TMPL_MESSAGE_SUBSCRIBER)
                            .adapterName(adapterName)
                            .exchangeName(exchangeName)
                            .eventName(eventName)
                            .logicName(logicName)
                            .ordered(ordered)
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
        return MessageRouteConfiguration.ROUTE_TMPL_MESSAGE_SUBSCRIBER + "-" + routeId;
    }
}