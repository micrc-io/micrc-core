package io.micrc.core.persistence.springboot;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.micrc.core.persistence.snowflake.MachineNumberAliveSchedule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * persistence auto configuration
 *
 * @author weiguan
 * @date 2022-09-02 20:40
 * @since 0.0.1
 */
@Configuration
@Import({
        MachineNumberAliveSchedule.class,
        PersistenceAutoRunner.class
})
public class PersistenceAutoConfiguration {

    @Value("${micrc.spring.memory-db.host}")
    private String host;

    @Value("${micrc.spring.memory-db.port}")
    private Integer port;

    @Value("${micrc.spring.memory-db.password}")
    private String password;

    @Value("${micrc.spring.memory-db.database}")
    private Integer database= 15;

    @Value("${micrc.spring.memory-db.username}")
    private String username;


    public RedisConnectionFactory memoryDbConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setDatabase(database);
        redisStandaloneConfiguration.setHostName(host);
        redisStandaloneConfiguration.setPort(port);
        redisStandaloneConfiguration.setUsername(username);
        redisStandaloneConfiguration.setPassword(RedisPassword.of(password));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisStandaloneConfiguration);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean("memoryDbTemplate")
    public RedisTemplate<Object, Object> memoryDbTemplate() {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(this.memoryDbConnectionFactory());
        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> valueSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        valueSerializer.setObjectMapper(objectMapper);
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

}
