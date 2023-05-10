package io.micrc.core.integration.runner;

import io.micrc.core.annotations.integration.runner.RunnerAdapter;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.HashMap;

@Aspect
@Configuration
public class RunnerAdapterRouterExecution implements Ordered {

    @EndpointInject
    private ProducerTemplate routeTemplate;

    @Pointcut("@annotation(io.micrc.core.annotations.integration.runner.RunnerExecution)")
    public void annotationPointCut() {/* leave it out */}

    @Around("annotationPointCut()")
    public void around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Class<?>[] interfaces = proceedingJoinPoint.getTarget().getClass().getInterfaces();
        if (interfaces.length != 1) { // 实现类有且只能有一个接口
            throw new IllegalStateException(
                    "application runner implementation class must only implement it's interface. ");
        }
        Object[] args = proceedingJoinPoint.getArgs();
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "method of application runner and annotated with RunnerAdapter only support single argument. ");
        }
        boolean custom = false;
        RunnerAdapter annotation = interfaces[0].getAnnotation(RunnerAdapter.class);
        if (annotation != null) {
            custom = annotation.custom();
        }
        if (custom || annotation == null) {
            proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
        } else {
            HashMap<String, Object> runnerBody = new HashMap<>(2);
            runnerBody.put("serviceName", annotation.serviceName());
            runnerBody.put("executePath", annotation.executePath());
            routeTemplate.requestBody(annotation.routeProtocol() + ":" + interfaces[0].getSimpleName(),
                    runnerBody);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
