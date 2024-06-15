package io.micrc.core.message.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import java.util.List;

/**
 * 消息幂等仓库
 *
 * @author hyosunghan
 * @version 0.0.1
 * @date 2022/12/2 9:11
 */
@Repository
@Transactional(rollbackFor = Exception.class)
public interface IdempotentMessageRepository extends JpaRepository<IdempotentMessage, Long> {

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    IdempotentMessage findFirstBySequenceAndReceiver(Long sequence, String receiver);

    /**
     * 查找所有发送方
     *
     * @return  sender
     */
    @Query(nativeQuery = true, value = "select distinct sender from message_idempotent_message for update nowait")
    List<String> findSender();

    /**
     * 清理入口
     *
     * @param sender    sender
     * @param count     count
     * @return          messageIds
     */
    @Query(nativeQuery = true,
            value = "select sequence from message_idempotent_message " +
                    "where sender=:sender " +
                    "order by sequence asc " +
                    "limit :count for update nowait")
    List<Long> findMessageIdsBySenderLimitCount(@Param("sender") String sender, @Param("count") Integer count);

    /**
     * 清理检查
     *
     * @param messageIds    messageIds
     * @param receiver      receiver
     * @return              messageIds
     */
    @Query(nativeQuery = true,
            value = "select sequence from message_idempotent_message " +
                    "where sequence in :messageIds " +
                    "and receiver=:receiver for update nowait")
    List<Long> filterMessageIdByMessageIdsAndReceiver(@Param("messageIds") List<Long> messageIds, @Param("receiver") String receiver);

    /**
     * 删除
     *
     * @param messageIds    messageIds
     * @return              count
     */
    Integer deleteAllBySequenceIn(@Param("messageIds") List<Long> messageIds);
}
