package com.demo.fixture.cache;

import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class TestEntity {
    @Id
    private String id;
}

@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class TestEntity2 {
    @Id
    private String id;
}
