package com.nageoffer.ai.ragent.framework.config;

import com.nageoffer.ai.ragent.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for web components.
 */
@Configuration
public class WebAutoConfiguration {

    /**
     * Registers the global exception handler.
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
