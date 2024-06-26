package io.micrc.core.integration.message.springboot;

import io.micrc.core.annotations.message.Adapter;
import io.micrc.core.annotations.message.MessageAdapter;
import io.micrc.core.integration.message.EnableMessageAdapter;
import io.micrc.core.integration.message.MessageAdapterRouteConfiguration;
import io.micrc.core.integration.message.MessageAdapterRouteConfiguration.ApplicationMessageRouteTemplateParamDefinition;
import io.micrc.core.integration.message.MessageAdapterRouteTemplateParameterSource;
import io.micrc.core.integration.message.MethodAdapterDesignException;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 消息接收适配器路由参数源bean注册器
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Component
public class ClassPathMessageAdapterScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableMessageAdapter.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        MessageAdapterRouteTemplateParameterSource source = new MessageAdapterRouteTemplateParameterSource();

        // application business service scanner
        ApplicationMessageAdapterScanner applicationMessageAdapterScanner = new ApplicationMessageAdapterScanner(
                registry, source);
        applicationMessageAdapterScanner.setResourceLoader(resourceLoader);
        applicationMessageAdapterScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<MessageAdapterRouteTemplateParameterSource>) source.getClass(),
                        () -> source)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(beanDefinition, registry),
                beanDefinition);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

}

/**
 * 消息接收适配注解扫描器
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
class ApplicationMessageAdapterScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final MessageAdapterRouteTemplateParameterSource sourceDefinition;

    public ApplicationMessageAdapterScanner(BeanDefinitionRegistry registry,
                                            MessageAdapterRouteTemplateParameterSource source) {
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
            if (beanDefinition.getBeanClass().getAnnotation(MessageAdapter.class).custom()) {
                continue;
            }
            String adapterName = beanDefinition.getBeanClass().getSimpleName();
            Method[] adapterMethods = beanDefinition.getBeanClass().getDeclaredMethods();
            Boolean haveAdaptMethod = Arrays.stream(adapterMethods).anyMatch(method -> {
                if ("adapt".equals(method.getName())) {
                    return true;
                }
                return false;
            });
            if (!haveAdaptMethod || adapterMethods.length != 1) {
                throw new MethodAdapterDesignException(" the message adapter interface " + adapterName
                        + " need extends MessageIntegrationAdapter. please check");
            }
            MessageAdapter messageAdapter = beanDefinition.getBeanClass().getAnnotation(MessageAdapter.class);
            Adapter[] adapters = messageAdapter.value();
            Arrays.stream(adapters).forEach(adapter-> {
                String servicePath = adapter.commandServicePath();
                String[] servicePathSplit = servicePath.split("\\.");
                String serviceName = servicePathSplit[servicePathSplit.length - 1];
                Class<?> service = null;
                try {
                    service = Class.forName(servicePath);
                } catch (ClassNotFoundException e) {
                    logger.warn("/******************************************/");
                    logger.warn("in dev,can not find class " + servicePath+",if env is ga, this is a bug, no checked");
                    logger.warn("/******************************************/");
                    return;
                }

                Method[] serviceMethods = service.getDeclaredMethods();
                List<Method> haveExecuteMethod = Arrays.stream(serviceMethods)
                        .filter(method -> "execute".equals(method.getName()) && !method.isBridge())
                        .collect(Collectors.toList());
                if (haveExecuteMethod.size() != 1) {
                    throw new MethodAdapterDesignException(" the application service interface " + servicePath
                            + " need extends ApplicationBusinessesService. please check");
                }
                Class<?>[] serviceMethodParameterTypes = serviceMethods[0].getParameterTypes();
                if (serviceMethodParameterTypes.length != 1) {
                    throw new MethodAdapterDesignException(" the message endpoint service interface " + servicePath
                            + " method execute param only can use command and only one param. please check");
                }
                String commandPath = serviceMethodParameterTypes[0].getName();

                String messageAdapterPath = adapterName + "-" + adapter.eventName() + "-" + serviceName;
                sourceDefinition.addParameter(
                        routeId(messageAdapterPath),
                        ApplicationMessageRouteTemplateParamDefinition.builder()
                                .templateId(MessageAdapterRouteConfiguration.ROUTE_TMPL_MESSAGE_ADAPTER)
                                .name(messageAdapterPath)
                                .commandPath(commandPath)
                                .serviceName(serviceName)
                                .event(adapter.eventName())
//                            .ordered(messageAdapter.ordered())
//                            .receiveEntityName(messageAdapter.rootEntityName())
                                .build());
            });
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
        return MessageAdapterRouteConfiguration.ROUTE_TMPL_MESSAGE_ADAPTER + "-" + routeId;
    }

}
