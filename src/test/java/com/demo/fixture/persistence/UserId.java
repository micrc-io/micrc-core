package com.demo.fixture.persistence;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import io.micrc.core.persistence.IdentityAware;
import lombok.Data;

@Data
@Embeddable
public class UserId implements Serializable, IdentityAware {
    @Column(name = "USERID")
    private long id;

    @Override
    public void setIdentity(long id) {
        this.id = id;
    }
}
