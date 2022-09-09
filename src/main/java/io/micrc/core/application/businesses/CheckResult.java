package io.micrc.core.application.businesses;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检查结果
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/8/29 15:51
 */
@Data
@EqualsAndHashCode
public class CheckResult {

    /**
     * 检查是否通过
     */
    private Boolean checkResult;

    /**
     * 检查错误码
     */
    private String errorCode;

    /**
     * 检查错误信息
     */
    private String errorMessage;
}
