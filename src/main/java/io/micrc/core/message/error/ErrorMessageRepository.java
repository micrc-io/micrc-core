package io.micrc.core.message.error;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 事件存储仓库
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/1/12 9:11 PM
 */
@Repository
public interface ErrorMessageRepository extends JpaRepository<ErrorMessage, Long> {

    @Query(nativeQuery = true,
           value = "select * from message_error_message " +
                   "where " +
                   "topic=:topic " +
                   "and sender=:sender " +
                   "and error_status='WAITING' " +
                   "order by message_id ASC " +
                   "limit :count ")
    List<ErrorMessage> findErrorMessageByTopicAndSenderLimitByCount(@Param("topic")String topic, @Param("sender")String sender, @Param("count")Integer count);

    ErrorMessage findFirstByMessageIdAndGroupId(@Param("messageId")Long messageId, @Param("groupId")String groupId);

    void deleteByMessageIdAndGroupId(@Param("messageId")Long messageId, @Param("groupId")String groupId);
}
