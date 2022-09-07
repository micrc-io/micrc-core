package io.micrc.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import io.micrc.core.message.springboot.MessageAutoConfiguration;
import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;
import io.micrc.core.rpc.springboot.RpcAutoConfiguration;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({ PersistenceAutoConfiguration.class, RpcAutoConfiguration.class, MessageAutoConfiguration.class })
public @interface EnableMicrcSupport {
    
}
