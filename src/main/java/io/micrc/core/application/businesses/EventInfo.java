package io.micrc.core.application.businesses;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 事件信息
 *
 * @author tengwang
 * @date 2022/10/11 21:30
 * @since 0.0.1
 */
@Data
public class EventInfo {

    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 预约时间，可选指定事件的发送时间
     */
    private Long appointmentTime;

    /**
     * 批量数据，对于一次业务需要发送多个批量事件的场景，该数据会自动拆分放在Command中用@BatchProperty标识的字段上，循环存储
     */
    private List<Object> eventBatchData = new ArrayList<>();
}
