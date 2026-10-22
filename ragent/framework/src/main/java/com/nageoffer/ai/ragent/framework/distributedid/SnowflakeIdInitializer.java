package com.nageoffer.ai.ragent.framework.distributedid;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.lang.Snowflake;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Distributed Snowflake initializer.
 * Loads workerId and datacenterId from Redis and registers them with Hutool IdUtil.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnowflakeIdInitializer {

    private final StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @PostConstruct
    public void init() {
        // Load the Lua script.
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/snowflake_init.lua")));
        script.setResultType(List.class);

        try {
            // Run the Lua script to obtain workerId and datacenterId.
            List<Long> result = stringRedisTemplate.execute(script, Collections.emptyList());

            if (CollUtil.isEmpty(result) || result.size() != 2) {
                throw new RuntimeException("Failed to load workerId and dataCenterId from Redis");
            }

            Long workerId = result.get(0);
            Long datacenterId = result.get(1);

            // Register the generator with Hutool IdUtil.
            Snowflake snowflake = new Snowflake(workerId, datacenterId);
            Singleton.put(snowflake);

            log.info("Distributed Snowflake initialized, workerId: {}, datacenterId: {}", workerId, datacenterId);
        } catch (Exception e) {
            throw new RuntimeException("Distributed Snowflake initialization failed", e);
        }
    }
}
