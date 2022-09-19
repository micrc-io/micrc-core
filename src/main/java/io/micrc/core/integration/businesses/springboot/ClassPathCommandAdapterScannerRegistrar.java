package io.micrc.core.integration.businesses.springboot;

import io.micrc.core.annotations.integration.CommandAdapter;
import io.micrc.core.annotations.integration.Conception;
import io.micrc.core.framework.json.JsonUtil;
import io.micrc.core.integration.businesses.ApplicationCommandAdapterRouteConfiguration;
import io.micrc.core.integration.businesses.ApplicationCommandAdapterRouteTemplateParameterSource;
import io.micrc.core.integration.businesses.ConceptionParam;
import io.micrc.core.integration.businesses.EnableCommandAdapter;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用业务服务路由参数源bean注册器
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Component
public class ClassPathCommandAdapterScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableCommandAdapter.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        ApplicationCommandAdapterRouteTemplateParameterSource source =
                new ApplicationCommandAdapterRouteTemplateParameterSource();

        // application business service scanner
        ApplicationCommandAdapterScanner applicationCommandAdapterScanner =
                new ApplicationCommandAdapterScanner(registry, source);
        applicationCommandAdapterScanner.setResourceLoader(resourceLoader);
        applicationCommandAdapterScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<ApplicationCommandAdapterRouteTemplateParameterSource>) source.getClass(),
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
 * 应用业务服务适配器注解扫描器，用于扫描@MessageAdapter及其exec方法的command参数类型和属性的注解
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
class ApplicationCommandAdapterScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final ApplicationCommandAdapterRouteTemplateParameterSource sourceDefinition;

    public ApplicationCommandAdapterScanner(BeanDefinitionRegistry registry,
                                            ApplicationCommandAdapterRouteTemplateParameterSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(CommandAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            if (beanDefinition.getBeanClass().getAnnotation(CommandAdapter.class).custom()) {
                continue;
            }
            String name = beanDefinition.getBeanClass().getSimpleName();
            Method[] adapterMethods = beanDefinition.getBeanClass().getDeclaredMethods();
            Boolean haveAdaptMethod = Arrays.stream(adapterMethods).anyMatch(method -> {
                if ("adapt".equals(method.getName())) {
                    return true;
                }
                return false;
            });
//            if (!haveAdaptMethod || adapterMethods.length != 2) {
//                throw new MethodAdapterDesignException(" the application businesses adapter interface " + name + " need extends ApplicationCommandAdapter. please check");
//            }
            CommandAdapter commandAdapter = beanDefinition.getBeanClass().getAnnotation(CommandAdapter.class);
            String serviceName = commandAdapter.serviceName();
            String servicePath = basePackages[0] + ".application.businesses." + commandAdapter.rootEntityName().toLowerCase() + "." + serviceName;
            Class<?> service = Class.forName(servicePath);
            if (null == service) {
                throw new ClassNotFoundException(" the application service interface " + servicePath + " not exist. please check");
            }

            Method[] serviceMethods = service.getDeclaredMethods();
            Class<?>[] serviceMethodParameterTypes = serviceMethods[0].getParameterTypes();
            if (serviceMethodParameterTypes.length != 1) {
                throw new MethodAdapterDesignException(" the application businesses adapter endpoint service interface " + commandAdapter.serviceName() + " method execute param only can use command and only one param. please check");
            }
            String commandPath = serviceMethodParameterTypes[0].getName();
            Conception[] conceptionAnnotations = commandAdapter.conceptions();
            List<ConceptionParam> conceptions = new ArrayList<>();
            Arrays.stream(conceptionAnnotations).forEach(conceptionAnnotation -> {
                String commandInnerName = "";
                if (commandInnerName.equals(conceptionAnnotation.commandInnerName())) {
                    commandInnerName = conceptionAnnotation.name();
                } else {
                    commandInnerName = conceptionAnnotation.commandInnerName();
                }
                ConceptionParam conceptionParam = new ConceptionParam();
                conceptionParam.setName(conceptionAnnotation.name());
                conceptionParam.setCommandInnerName(commandInnerName);
                conceptionParam.setOrder(conceptionAnnotation.order());
                conceptionParam.setTargetConceptionMappingPath(conceptionAnnotation.targetConceptionMappingPath());
                conceptionParam.setResolved(false);
                conceptions.add(conceptionParam);
            });
            sourceDefinition.addParameter(
                    routeId(beanDefinition.getBeanClass().getSimpleName()),
                    ApplicationCommandAdapterRouteConfiguration.ApplicationCommandRouteTemplateParamDefinition.builder()
                            .templateId(ApplicationCommandAdapterRouteConfiguration.ROUTE_TMPL_BUSINESSES_ADAPTER)
                            .name(name)
                            .serviceName(serviceName)
                            .commandPath(commandPath)
                            .conceptionsJson(JsonUtil.writeValueAsString(conceptions))
                            .build());
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
        return ApplicationCommandAdapterRouteConfiguration.ROUTE_TMPL_BUSINESSES_ADAPTER + "-" + routeId;
    }
}

