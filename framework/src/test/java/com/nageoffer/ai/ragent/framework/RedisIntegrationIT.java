package com.nageoffer.ai.ragent.framework;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Framework integration test. Redis comes from Testcontainers. No model client is on this classpath.
 */
@SpringBootTest(
        classes = RedisIntegrationIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Testcontainers
class RedisIntegrationIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "it-secret");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "it-secret");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void roundTripsAValueThroughRedis() {
        redisTemplate.opsForValue().set("ragent:it", "ok");

        assertThat(redisTemplate.opsForValue().get("ragent:it")).isEqualTo("ok");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
    })
    static class TestApplication {
    }
}
