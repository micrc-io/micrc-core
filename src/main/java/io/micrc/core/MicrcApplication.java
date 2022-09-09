package io.micrc.core;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.h2.tools.Server;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

import lombok.extern.java.Log;

@Log
public final class MicrcApplication {
    private MicrcApplication() {
    }

    public static void run(Class<?> appClazz, String[] args) {
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        if (argList.contains("dbinit")) {
            new SpringApplicationBuilder(LiquibaseInit.class)
                .contextFactory(ApplicationContextFactory
                    .ofContextClass(AnnotationConfigApplicationContext.class))
                .profiles("dbinit")
                .run(args);
            return;
        }
        if (argList.contains("local")) {
            startH2Server();
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

    private static void startH2Server() {
        try {
            Server h2 = Server.createTcpServer("-tcp", "-tcpAllowOthers").start();
            if (h2.isRunning(true)) {
                log.info("H2 database tcp server was started and is running at: " + h2.getURL());
                return;
            }
            throw new IllegalStateException("Could not start H2 server.");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed not start H2 server. ", e);
        }
    }
}

@Import({ DataSourceAutoConfiguration.class, LiquibaseAutoConfiguration.class })
class LiquibaseInit {}
