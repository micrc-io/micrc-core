package io.micrc.core.integration.command.businesses;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 概念
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/9/5 20:07
 */
@Data
@NoArgsConstructor
public class ConceptionParam {

    private String name;

    private Integer order;

    private String commandInnerName;

    private String targetConceptionMappingPath;

    private Boolean resolved;
}
