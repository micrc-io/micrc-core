package com.demo.fixture.persistence;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import io.micrc.core.persistence.MicrcJpaRepository;
import lombok.Data;

@CacheConfig(cacheNames = { "aggre-repo" })
@Repository
public interface TestEntityRepository extends MicrcJpaRepository<Users, String> {
    @Cacheable(cacheResolver = "repositoryCacheResolver", keyGenerator = "repositoryQueryKeyGenerator", sync = true)
    Page<Users> findByUsername(String username, Pageable pageable);
}

@Data
@Entity
class Users implements Serializable {
    @Id
    @Column(name = "USERID")
    private String id;
    @Column(name = "USERNAME")
    private String username;
}

@Component
class TestRepo extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("rest:get:testentity/save").setBody().constant("{\"id\":\"xxx\",\"username\":\"test\"}")
            .unmarshal().json(Users.class)
            .bean("testEntityRepository", "save(${body})")
            .log("${body}");
        from("rest:get:testentity/findbyid").setBody().constant("xxx")
            .bean("testEntityRepository", "findById(${body})")
            .log("${body}");
        from("rest:get:testentity/count")
            .bean("testEntityRepository", "count()")
            .log("${body}");
        from("rest:get:testentity/exists").setBody().constant("xxx")
            .bean("testEntityRepository", "existsById(${body})")
            .log("${body}");
    }
}
