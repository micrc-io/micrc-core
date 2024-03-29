package io.micrc.core.message.error;

import io.micrc.core.persistence.snowflake.SnowFlakeIdentity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 异常消息-包含发送失败与消费失败消息
 *
 * @author hyosunghan
 * @date 2022/12/2 15:22
 * @since 0.0.1
 */
@Slf4j
@Data
@Entity
@NoArgsConstructor
@Table(name = "message_error_message")
public class ErrorMessage {

    /**
     * 失败消息ID
     */
    @Id
    private Long errorMessageId = SnowFlakeIdentity.getInstance().nextId();

    /**
     * 序列号
     */
    private Long messageId;

    /**
     * 组ID
     */
    private String groupId;

    /**
     * 事件
     */
    private String event;

    /**
     * 发送内容
     */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /**
     * 原始映射
     */
    @Column(columnDefinition = "json")
    @Type(type = "json")
    private String originalMapping;

    /**
     * 原始主题
     */
    private String originalTopic;

    /**
     * 错误次数
     */
    private Integer errorCount = 1;

    /**
     * 错误信息
     */
    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    /**
     * 错误状态,WAITING,SENDING,STOP
     */
    private String errorStatus;

}