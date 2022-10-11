package io.micrc.core.application.businesses;

import io.micrc.core.rpc.ErrorInfo;
import lombok.Data;

/**
 * 基Command
 *
 * @author tengwang
 * @date 2022/10/11 21:33
 * @since 0.0.1
 */
@Data
public class MicrcCommand {

    private ErrorInfo error;

    private EventInfo event;
}
