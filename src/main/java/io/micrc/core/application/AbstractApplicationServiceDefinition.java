package io.micrc.core.application;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public abstract class AbstractApplicationServiceDefinition {
    protected String templateId;
}
