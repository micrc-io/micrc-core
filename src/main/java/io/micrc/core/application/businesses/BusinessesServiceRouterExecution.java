package io.micrc.core.application.businesses;

import io.micrc.core.annotations.application.businesses.BusinessesService;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Map;

@Aspect
@Configuration
public class BusinessesServiceRouterExecution implements Ordered {
    private static Map<String, Object> routeHeaders = Map.of("WrappedRouter", true);

    @EndpointInject
    private ProducerTemplate routeTemplate;

    @Pointcut("@annotation(io.micrc.core.annotations.application.businesses.BusinessesExecution)")
    public void annotationPointCut() {/* leave it out */}

    @Around("annotationPointCut()")
    public void around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Class<?>[] interfaces = proceedingJoinPoint.getTarget().getClass().getInterfaces();
        if (interfaces.length != 1) { // 实现类有且只能有一个接口
            throw new IllegalStateException(
                    "businesses service implementation class must only implement it's interface. ");
        }
        Object[] args = proceedingJoinPoint.getArgs();
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "method of businesses service and annotated with BusinessesService only support single argument. ");
        }
        boolean custom = false;
        BusinessesService annotation = interfaces[0].getAnnotation(BusinessesService.class);
        if (annotation != null) {
            custom = annotation.custom();
        }
        if (custom || annotation == null) {
            proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
        } else {
            routeTemplate.requestBodyAndHeaders(
                    annotation.routeProtocol() + ":" + interfaces[0].getSimpleName(),
                    args[0],
                    routeHeaders
            );
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
