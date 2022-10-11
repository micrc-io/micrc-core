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
     * 批量数据
     */
    private List<Object> eventBatchData = new ArrayList<>();
}
