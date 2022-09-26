package com.demo.fixture.persistence;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import io.micrc.core.persistence.MicrcJpaRepository;

@CacheConfig(cacheNames = { "aggre-repo" })
@Repository
public interface TestEntityRepository extends MicrcJpaRepository<Users, UserId> {
    @Cacheable(cacheResolver = "repositoryCacheResolver", keyGenerator = "repositoryQueryKeyGenerator", sync = true)
    Page<Users> findByUsername(String username, Pageable pageable);
}

@Component
class TestRepo extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("rest:get:testentity/id").setBody().constant(UserId.class.getName())
            .bean("testEntityRepository", "id(String)")
            .log("${body}");
        from("rest:get:testentity/save")
            .setBody().constant("{\"embeddedId\":{\"id\":123},\"username\":\"test\"}")
            .unmarshal().json(Users.class)
            .bean("testEntityRepository", "save(${body})")
            .log("${body}");
        from("rest:get:testentity/findbyid").setBody().constant("{\"id\":123}")
            .unmarshal().json(UserId.class)
            .bean("testEntityRepository", "findById(${body})")
            .log("${body}");
        from("rest:get:testentity/count")
            .bean("testEntityRepository", "count()")
            .log("${body}");
        from("rest:get:testentity/exists").setBody().constant("{\"id\":123}")
            .unmarshal().json(UserId.class)
            .bean("testEntityRepository", "existsById(${body})")
            .log("${body}");
    }
}
