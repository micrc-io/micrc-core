package io.micrc.core.persistence;

import lombok.Data;

import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * 嵌入式标识符
 *
 * @author hyosunghan
 * @date 2022/9/29 10:07
 * @since 0.0.1
 */
@Data
@Embeddable
public class EmbeddedIdentity implements Serializable, IdentityAware {

    /**
     * ID
     */
    private Long id;

    @Override
    public void setIdentity(long id) {
        this.id = id;
    }
}
