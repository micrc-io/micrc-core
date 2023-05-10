package io.micrc.core.persistence.springboot;

import io.micrc.core.persistence.snowflake.SnowFlakeIdentity;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * persistence auto configuration
 *
 * @author weiguan
 * @date 2022-09-02 20:40
 * @since 0.0.1
 */
@Order(value = -1)
public class PersistenceAutoRunner implements ApplicationRunner {

    /**
     * 机器码键前缀
     */
    public static final String MACHINE_NUMBER_KEY_PREFIX = "MACHINE:NUMBER:";

    @Resource(name = "memoryDbTemplate")
    RedisTemplate<Object, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        allotMachineNumber();
    }

    private void allotMachineNumber() {
        Integer number = null;
        for (int i = 0; i <= SnowFlakeIdentity.MAX_MACHINE_NUMBER; i++) {
            String key = MACHINE_NUMBER_KEY_PREFIX + i;
            String value = (String) redisTemplate.opsForValue().get(key);
            if (value != null) {
                continue;
            }
            Boolean done = redisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.MINUTES);
            if (Boolean.TRUE.equals(done)) {
                number = i;
                break;
            }
        }
        if (number == null) {
            throw new IllegalStateException("There is no machine number can used.");
        }
        SnowFlakeIdentity.machineNumber = number;
    }

}
