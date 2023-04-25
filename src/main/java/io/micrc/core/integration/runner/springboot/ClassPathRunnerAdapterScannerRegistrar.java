package io.micrc.core.integration.runner.springboot;

import io.micrc.core.annotations.integration.runner.RunnerAdapter;
import io.micrc.core.integration.message.MethodAdapterDesignException;
import io.micrc.core.integration.runner.RunnerAdapterRouteConfiguration;
import io.micrc.core.integration.runner.RunnerAdapterRouteTemplateParameterSource;
import io.micrc.core.integration.runner.EnableRunnerAdapter;
import io.micrc.lib.FileUtils;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
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

@Component
public class ClassPathRunnerAdapterScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        @NotNull BeanDefinitionRegistry registry,
                                        @NotNull BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableRunnerAdapter.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        RunnerAdapterRouteTemplateParameterSource source =
                new RunnerAdapterRouteTemplateParameterSource();

        // application business service scanner
        ApplicationRunnerAdapterScanner applicationRunnerAdapterScanner =
                new ApplicationRunnerAdapterScanner(registry, source);
        applicationRunnerAdapterScanner.setResourceLoader(resourceLoader);
        applicationRunnerAdapterScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<RunnerAdapterRouteTemplateParameterSource>) source.getClass(),
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

class ApplicationRunnerAdapterScanner extends ClassPathBeanDefinitionScanner {

    private static final AtomicInteger INDEX = new AtomicInteger();

    private final RunnerAdapterRouteTemplateParameterSource sourceDefinition;

    public ApplicationRunnerAdapterScanner(BeanDefinitionRegistry registry,
                                           RunnerAdapterRouteTemplateParameterSource source) {
        super(registry, false);
        this.sourceDefinition = source;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isInterface() && metadata.isIndependent();
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry routersInfo) {
        // nothing to do. leave it out.
    }

    @SneakyThrows
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        this.addIncludeFilter(new AnnotationTypeFilter(RunnerAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            Class<?> beanClass = beanDefinition.getBeanClass();
            String adapterName = beanClass.getSimpleName();
            Method[] adapterMethods = beanClass.getMethods();
            boolean haveAdaptMethod = Arrays.stream(adapterMethods).anyMatch(method -> "run".equals(method.getName()));
            if (!haveAdaptMethod || adapterMethods.length != 1) {
                throw new IllegalStateException(" the runner adapter interface " + adapterName + " need extends RunnerIntegrationAdapter. please check");
            }
            RunnerAdapter runnerAdapter = beanClass.getAnnotation(RunnerAdapter.class);
            // 需要自定义的服务不生成路由
            if (runnerAdapter.custom()) {
                continue;
            }
            String serviceName = runnerAdapter.serviceName();
            String servicePath = beanClass.getPackageName().replace("infrastructure.runner", "application.businesses") + "." + serviceName;
            Class<?> service = Class.forName(servicePath);
            Method[] serviceMethods = service.getDeclaredMethods();
            Class<?>[] serviceMethodParameterTypes = serviceMethods[0].getParameterTypes();
            if (serviceMethodParameterTypes.length != 1) {
                throw new MethodAdapterDesignException(" the application businesses adapter endpoint service interface " + runnerAdapter.serviceName() + " method execute param only can use command and only one param. please check");
            }
            String commandPath = serviceMethodParameterTypes[0].getName();
            String executeContent = "{}";
            String executePath = runnerAdapter.executePath();
            if (StringUtils.hasText(executePath)) {
                executeContent = FileUtils.fileReader(executePath, List.of("json"));
            }
            sourceDefinition.addParameter(
                    routeId(adapterName),
                    RunnerAdapterRouteConfiguration.RunnerRouteTemplateParamDefinition.builder()
                            .templateId(RunnerAdapterRouteConfiguration.ROUTE_TMPL_RUNNER_ADAPTER)
                            .name(adapterName)
                            .serviceName(serviceName)
                            .executeContent(executeContent)
                            .commandPath(commandPath)
                            .build());
        }
        holders.clear();
        return holders;
    }

    private String routeId(String id) {
        String routeId = id;
        if (!StringUtils.hasText(routeId)) {
            routeId = String.valueOf(INDEX.getAndIncrement());
        }
        return RunnerAdapterRouteConfiguration.ROUTE_TMPL_RUNNER_ADAPTER + "-" + routeId;
    }
}
