package io.micrc.core.message.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 事件存储仓库
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/1/12 9:11 PM
 */
@Repository
public interface ErrorMessageRepository extends JpaRepository<EventMessage, String> {

}
