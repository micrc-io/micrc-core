package io.micrc.core.message.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 消息跟踪器仓库
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/1/12 9:11 PM
 */
@Repository
public interface MessageTrackerRepository extends JpaRepository<MessageTracker, String> {

    MessageTracker findFirstByChannel(String channel);
}
