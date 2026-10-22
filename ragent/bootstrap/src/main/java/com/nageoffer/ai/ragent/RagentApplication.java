package com.nageoffer.ai.ragent;

import com.mzt.logapi.starter.annotation.EnableLogRecord;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ragent Core Starter
 */
@SpringBootApplication
@EnableScheduling
@EnableLogRecord(tenant = "ragent", proxyTargetClass = true)
@MapperScan(basePackages = {
        "com.nageoffer.ai.ragent.rag.dao.mapper",
        "com.nageoffer.ai.ragent.ingestion.dao.mapper",
        "com.nageoffer.ai.ragent.knowledge.dao.mapper",
        "com.nageoffer.ai.ragent.user.dao.mapper",
        "com.nageoffer.ai.ragent.audit.dao.mapper",
        "com.nageoffer.ai.ragent.sample.dao.mapper",
        "com.nageoffer.ai.ragent.agent.dao.mapper"
})
public class RagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagentApplication.class, args);
    }
}
