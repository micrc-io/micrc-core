package io.micrc.core.application.presentations.springboot;

import io.micrc.core.annotations.application.presentations.Integration;
import io.micrc.core.annotations.application.presentations.IntegrationMapping;
import io.micrc.core.annotations.application.presentations.PresentationsService;
import io.micrc.core.annotations.application.presentations.QueryLogic;
import io.micrc.core.application.presentations.ApplicationPresentationsServiceRouteConfiguration;
import io.micrc.core.application.presentations.ApplicationPresentationsServiceRouteConfiguration.ApplicationPresentationsServiceDefinition;
import io.micrc.core.application.presentations.ApplicationPresentationsServiceRouteTemplateParameterSource;
import io.micrc.core.application.presentations.EnablePresentationsService;
import io.micrc.core.application.presentations.ParamIntegration;
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
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 展示服务路由参数源bean注册器
 *
 * @author hyosunghan
 * @date 2022-9-2 13:04
 * @since 0.0.1
 */
@Component
public class ClassPathPresentationsServiceScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnablePresentationsService.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        ApplicationPresentationsServiceRouteTemplateParameterSource source =
                new ApplicationPresentationsServiceRouteTemplateParameterSource();

        // application business service scanner
        ApplicationPresentationsServiceScanner applicationPresentationsServiceScanner =
                new ApplicationPresentationsServiceScanner(registry, source);
        applicationPresentationsServiceScanner.setResourceLoader(resourceLoader);
        applicationPresentationsServiceScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<ApplicationPresentationsServiceRouteTemplateParameterSource>) source.getClass(),
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
 * 应用展示服务注解扫描器，用于扫描@PresentationsService及其exec方法的参数类型和属性的注解
 * 获取注解中的声明逻辑的属性，构造路由模版源，最终注入camel context用于构造执行路由
 *
 * @author hyosunghan
 * @date 2022-9-2 13:14
 * @since 0.0.1
 */
class ApplicationPresentationsServiceScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final ApplicationPresentationsServiceRouteTemplateParameterSource sourceDefinition;

    public ApplicationPresentationsServiceScanner(BeanDefinitionRegistry registry,
                                                  ApplicationPresentationsServiceRouteTemplateParameterSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(PresentationsService.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            // 检查方法数量，必须是单方法并且名称为"execute"
            List<Method> executeMethods = Arrays.stream(beanDefinition.getBeanClass().getMethods())
                    .filter(method -> method.getName().equals("execute")).collect(Collectors.toList());
            if (executeMethods.size() != 1) {
                throw new IllegalStateException("the " + beanDefinition.getBeanClass().getName() + " don`t have only one execute method, please check this application service....");
            }
            // 检查参数数量，必须是单参数
            Parameter[] parameters = executeMethods.get(0).getParameters();
            if (parameters.length != 1) {
                throw new IllegalStateException("the " + beanDefinition.getBeanClass().getName() + " execute method don`t have only one command param, please check this application service....");
            }
            PresentationsService presentationsService = beanDefinition.getBeanClass().getAnnotation(PresentationsService.class);
            // 需要自定义的服务不生成路由
            if (presentationsService.custom()) {
                continue;
            }
            // 解析参数所有集成
            List<ParamIntegration> paramIntegrations = getParamIntegrations(presentationsService.queryLogics(), presentationsService.integrations());
            // 检查展示服务注解至少需要包含一次集成/查询
            if (paramIntegrations.isEmpty()) {
                throw new IllegalStateException("the " + beanDefinition.getBeanClass().getName() + " annotation should be at least one integration or query");
            }
            // 获取ApplicationService注解参数
            String serviceName = beanDefinition.getBeanClass().getSimpleName();
            sourceDefinition.addParameter(
                    routeId(serviceName),
                    ApplicationPresentationsServiceDefinition.builder()
                            .templateId(ApplicationPresentationsServiceRouteConfiguration.ROUTE_TMPL_PRESENTATIONS_SERVICE)
                            .serviceName(serviceName)
                            .paramIntegrationsJson(JsonUtil.writeValueAsString(paramIntegrations))
                            .assembler(FileUtils.fileReader(presentationsService.assembler(), List.of("jslt")))
                            .build());
        }
        holders.clear();
        return holders;
    }

    /**
     * 获取参数集成，包括查询逻辑和集成
     *
     * @param queryLogics
     * @param integrations
     * @return
     */
    private List<ParamIntegration> getParamIntegrations(QueryLogic[] queryLogics, Integration[] integrations) {
        List<ParamIntegration> paramIntegrations = new ArrayList<>();
        // 查询逻辑解析
        Arrays.stream(queryLogics).forEach(logic -> {
            Map<String, String> paramPath = new HashMap<>();
            // 解析查询参数
            Arrays.stream(logic.params()).forEach(param -> {
                paramPath.put(param.toString(), param.path());
            });
            // 解析排序参数
            Map<String, String> sortParam = Arrays.stream(logic.sorts())
                    .collect(Collectors.toMap(i -> lowerStringFirst(i.belongConcept()), i -> i.type().name()));
            paramIntegrations.add(new ParamIntegration(logic.name(), lowerStringFirst(logic.aggregation()), logic.methodName(),
                    paramPath, sortParam, logic.pageSizePath(), logic.pageNumberPath(), logic.order()));
        });
        // 集成解析
        Arrays.stream(integrations).forEach(integration -> {
            // 参数映射
            Map<String, String> paramMappings = Arrays.stream(integration.integrationMapping()).collect(Collectors.toMap(IntegrationMapping::name, IntegrationMapping::mapping));
            paramIntegrations.add(new ParamIntegration(integration.name(), integration.protocol(), integration.order(), paramMappings));
        });
        // 按照优先级排序
        paramIntegrations.sort(Comparator.comparing(ParamIntegration::getOrder));
        return paramIntegrations;
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
        return ApplicationPresentationsServiceRouteConfiguration.ROUTE_TMPL_PRESENTATIONS_SERVICE + "-" + routeId;
    }

    /**
     * 首字母小写
     *
     * @param str
     * @return
     */
    private static String lowerStringFirst(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        char[] strChars = str.toCharArray();
        strChars[0] += 32;
        return String.valueOf(strChars);
    }
}
