package com.demo.fixture.persistence;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

import lombok.Data;

@Data
@Entity
public class Users implements Serializable {
    @EmbeddedId
    private UserId embeddedIdentity;
    @Column(name = "USERNAME")
    private String username;
}
