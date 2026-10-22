package com.nageoffer.ai.ragent.framework.config;

import com.nageoffer.ai.ragent.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web Auto Config
 */
@Configuration
public class WebAutoConfiguration {
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
