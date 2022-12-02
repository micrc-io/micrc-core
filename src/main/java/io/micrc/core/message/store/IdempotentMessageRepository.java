package io.micrc.core.message.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
