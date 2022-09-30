package io.micrc.core.application.businesses.springboot;

import io.micrc.core.annotations.application.businesses.*;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.ApplicationBusinessesServiceDefinition;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.CommandParamIntegration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.LogicIntegration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteTemplateParameterSource;
import io.micrc.core.application.businesses.EnableBusinessesService;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 应用业务服务路由参数源bean注册器
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Component
public class ClassPathBusinessesServiceScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableBusinessesService.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        ApplicationBusinessesServiceRouteTemplateParameterSource source =
                new ApplicationBusinessesServiceRouteTemplateParameterSource();

        // application business service scanner
        ApplicationBusinessesServiceScanner applicationBusinessesServiceScanner =
                new ApplicationBusinessesServiceScanner(registry, source);
        applicationBusinessesServiceScanner.setResourceLoader(resourceLoader);
        applicationBusinessesServiceScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<ApplicationBusinessesServiceRouteTemplateParameterSource>) source.getClass(),
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
 * 应用业务服务注解扫描器，用于扫描@BusinessesService及其exec方法的command参数类型和属性的注解
 * 获取注解中的声明逻辑的属性，构造路由模版源，最终注入camel context用于构造执行路由
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
class ApplicationBusinessesServiceScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final ApplicationBusinessesServiceRouteTemplateParameterSource sourceDefinition;

    public ApplicationBusinessesServiceScanner(BeanDefinitionRegistry registry,
                                               ApplicationBusinessesServiceRouteTemplateParameterSource source) {
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
            BusinessesService businessesService = beanDefinition.getBeanClass().getAnnotation(BusinessesService.class);
            if (businessesService.custom()) {
                continue;
            }
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
            List<Field> targetFields = Arrays.stream(commandFields).filter(field -> field.getName().equals("target")).collect(Collectors.toList());
            if (targetFields.size() != 1) {
                throw new RuntimeException("the " + parameters[0].getType() + " don`t have only one target field, please check this command....");
            }
            // 获取ApplicationService注解参数
            String serviceName = beanDefinition.getBeanClass().getSimpleName();
            String logicName = parameters[0].getType().getSimpleName().substring(0, parameters[0].getType().getSimpleName().length() - 7);
            String aggregationName = targetFields.get(0).getType().getSimpleName();
            String repositoryName = lowerStringFirst(aggregationName) + "Repository";
            String aggregationPath = targetFields.get(0).getType().getName();
            // 获取Command身上的注解
            CommandLogic commandLogic = parameters[0].getType().getAnnotation(CommandLogic.class);
            if (null == commandLogic) {
                throw new RuntimeException("the " + parameters[0].getType().getSimpleName() + "not have CommandLogic annotation, please check this command");
            }

            String targetIdPath = commandLogic.targetIdPath();
            LogicMapping[] logicMappings = commandLogic.toLogicMappings();
            TargetMapping[] targetMappings = commandLogic.toTargetMappings();

            if (null == targetMappings || targetMappings.length == 0) {
                throw new RuntimeException("the " + parameters[0].getType().getSimpleName() + "not have TargetMapping, please check this command");
            }
            Map<String, String> outMappingMap = new HashMap<>();
            Arrays.asList(logicMappings).forEach(logicMapping -> outMappingMap.put(logicMapping.name(), logicMapping.mapping()));
            Map<String, String> enterMappingMap = new HashMap<>();
            Arrays.stream(targetMappings).forEach(targetMapping -> enterMappingMap.put(targetMapping.name(), targetMapping.mapping()));
            LogicIntegration logicIntegration = LogicIntegration.builder().enterMappings(enterMappingMap).outMappings(outMappingMap).build();
            // 获取Command身上的参数的服务集成注解
            List<CommandParamIntegration> commandParamIntegrations = new ArrayList<>();
            Arrays.stream(commandFields).forEach(field -> {
                DeriveIntegration deriveIntegration = field.getAnnotation(DeriveIntegration.class);
                if (null != deriveIntegration) {
                    String objectTreePath = deriveIntegration.objectTreePath();
                    if ("/".equals(objectTreePath)) {
                        objectTreePath = objectTreePath + field.getName();
                    }
                    CommandParamIntegration commandParamIntegration = CommandParamIntegration.builder().paramName(field.getName()).objectTreePath(objectTreePath).protocol(deriveIntegration.protocolPath()).build();
                    commandParamIntegrations.add(commandParamIntegration);
                }
            });
            // 获取嵌套标识符全类名
            Class<?> repositoryClass = Class.forName(businessesService.repositoryFullClassName());
            ParameterizedType genericInterface = (ParameterizedType) (repositoryClass.getGenericInterfaces()[0]);
            String embeddedIdentityFullClassName = genericInterface.getActualTypeArguments()[1].getTypeName();

            String logicIntegrationJson = Base64.getEncoder().encodeToString(JsonUtil.writeValueAsString(logicIntegration).getBytes());

            sourceDefinition.addParameter(
                    routeId(serviceName),
                    ApplicationBusinessesServiceDefinition.builder()
                            .templateId(ApplicationBusinessesServiceRouteConfiguration.ROUTE_TMPL_BUSINESSES_SERVICE)
                            .serviceName(serviceName)
                            .logicName(logicName)
                            .targetIdPath(targetIdPath)
                            .aggregationName(aggregationName)
                            .repositoryName(repositoryName)
                            .aggregationPath(aggregationPath)
                            .logicName(logicName)
                            .logicIntegrationJson(logicIntegrationJson)
                            .embeddedIdentityFullClassName(embeddedIdentityFullClassName)
                            .commandParamIntegrationsJson(JsonUtil.writeValueAsString(commandParamIntegrations))
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
        return ApplicationBusinessesServiceRouteConfiguration.ROUTE_TMPL_BUSINESSES_SERVICE + "-" + routeId;
    }

    private String lowerStringFirst(String str) {
        char[] strChars = str.toCharArray();
        strChars[0] += 32;
        return String.valueOf(strChars);
    }
}
