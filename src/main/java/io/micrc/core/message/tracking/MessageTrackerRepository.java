package io.micrc.core.message.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 消息跟踪器仓库
 *
 * @author hyosunghan
 * @version 0.0.1
 * @date 2022/12/01 9:11
 */
@Repository
public interface MessageTrackerRepository extends JpaRepository<MessageTracker, String> {

    MessageTracker findFirstBySenderNameAndTopicName(String senderName, String topicName);
}
