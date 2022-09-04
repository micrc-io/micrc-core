package io.micrc.core.persistence.springboot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnBean(BuildProperties.class)
public class PersistenceAutoConfiguration implements EnvironmentAware {

    @Autowired
    private BuildProperties properties;

    public PersistenceAutoConfiguration() {
        System.out.println(properties);
        System.out.println("Test");
    }

    @Override
    public void setEnvironment(Environment environment) {
        System.out.println(environment);
    }
}
