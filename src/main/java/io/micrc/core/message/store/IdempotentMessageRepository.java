package io.micrc.core.message.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息幂等仓库
 *
 * @author hyosunghan
 * @version 0.0.1
 * @date 2022/12/2 9:11
 */
@Repository
public interface IdempotentMessageRepository extends JpaRepository<IdempotentMessage, Long> {

    IdempotentMessage findFirstBySequenceAndReceiver(Long sequence, String receiver);

    /**
     * 清理入口
     *
     * @return
     */
    @Query(nativeQuery = true,
            value = "select sequence from message_idempotent_message " +
                    "order by sequence asc " +
                    "limit :count")
    List<Long> findMessageIdsLimitCount(@Param("count") Integer count);

    /**
     * 清理检查
     *
     * @param messageIds
     * @param receiver
     * @return
     */
    @Query(nativeQuery = true,
            value = "select sequence from message_idempotent_message " +
                    "where sequence in :messageIds " +
                    "and receiver=:receiver ")
    List<Long> filterMessageIdByMessageIdsAndReceiver(@Param("messageIds") List<Long> messageIds, @Param("receiver") String receiver);

    /**
     * 删除
     *
     * @param messageIds
     * @return
     */
    Integer deleteAllBySequenceIn(@Param("messageIds") List<Long> messageIds);
}
