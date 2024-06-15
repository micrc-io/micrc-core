package io.micrc.core.message.error;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 事件存储仓库
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/1/12 9:11 PM
 */
@Repository
@Transactional(rollbackFor = Exception.class)
public interface ErrorMessageRepository extends JpaRepository<ErrorMessage, Long> {

    @Query(nativeQuery = true,
           value = "select * from message_error_message " +
                   "where " +
                   "event=:event " +
                   "and error_status='WAITING' " +
                   "order by message_id ASC " +
                   "limit :count for update nowait")
    List<ErrorMessage> findErrorMessageByEventLimitByCount(@Param("event")String event, @Param("count")Integer count);

    @Query(nativeQuery = true,
            value = "select * from message_error_message " +
                    "where " +
                    "original_topic is not null " +
                    "and error_status='WAITING' " +
                    "order by message_id ASC " +
                    "limit 100 for update nowait")
    List<ErrorMessage> findErrorMessageByOriginalExists();

    ErrorMessage findFirstByMessageIdAndGroupId(@Param("messageId")Long messageId, @Param("groupId")String groupId);

    void deleteByMessageIdAndGroupId(@Param("messageId")Long messageId, @Param("groupId")String groupId);
}
