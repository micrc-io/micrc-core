package io.micrc.core.persistence.springboot;

import io.micrc.core.persistence.snowflake.MachineNumberAliveSchedule;
import io.micrc.core.persistence.snowflake.SnowFlakeIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * persistence auto configuration
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-02 20:40
 */
@Configuration
@Import({
        MachineNumberAliveSchedule.class
})
public class PersistenceAutoConfiguration implements ApplicationRunner {

    /**
     * 机器码键前缀
     */
    public static final String MACHINE_NUMBER_KEY_PREFIX = "MACHINE:NUMBER:";

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        allotMachineNumber();
    }

    private void allotMachineNumber() {
        Integer number = null;
        for (int i = 0; i <= SnowFlakeIdentity.MAX_MACHINE_NUMBER; i++) {
            String key = MACHINE_NUMBER_KEY_PREFIX + i;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                continue;
            }
            Boolean done = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.MINUTES);
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
