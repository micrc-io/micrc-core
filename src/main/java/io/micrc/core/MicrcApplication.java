package io.micrc.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * micrc core application
 * support default(local), local(local cluster), dev(dev cluster),
 *         production(pre-/production cluster), verify(test cluster) profile
 * support dbinit standalone app in container
 * start h2 tcp server for local profile with local cluster env
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-25 22:42
 */
public final class MicrcApplication {
    private MicrcApplication() {
    }

    public static void run(Class<?> appClazz, String[] args) {
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        if (argList.contains("dbinit")) {
            new SpringApplicationBuilder(LiquibaseInit.class)
                .properties("spring.profiles.active=dbinit")
                .contextFactory(ApplicationContextFactory
                    .ofContextClass(AnnotationConfigApplicationContext.class))
                .profiles("dbinit")
                .run(args);
            return;
        }
        if (argList.contains("local")) {
            argList.add("--spring.profiles.active=local");
            SpringApplication.run(appClazz, argList.toArray(String[]::new));
            return;
        }
        if (argList.contains("dev")) {
            argList.add("--spring.profiles.active=dev");
            SpringApplication.run(appClazz, argList.toArray(String[]::new));
            return;
        }
        if (argList.contains("production")) {
            argList.add("--spring.profiles.active=production");
            SpringApplication.run(appClazz, argList.toArray(String[]::new));
            return;
        }
        if (argList.contains("verify")) {
            argList.add("--spring.profiles.active=verify");
            SpringApplication.run(appClazz, argList.toArray(String[]::new));
            return;
        }
        SpringApplication.run(appClazz, args);
    }
}

@Import({ DataSourceAutoConfiguration.class, LiquibaseAutoConfiguration.class })
class LiquibaseInit {}
