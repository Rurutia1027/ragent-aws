package com.nageoffer.ai.ragent.framework.config;

import com.nageoffer.ai.ragent.framework.mq.MqTraceRecordInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.stereotype.Component;

/**
 * Installs the interceptor that restores trace headers on the Kafka listener container.
 */
@Component
public class KafkaTraceConfiguration implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
            @SuppressWarnings("unchecked")
            ConcurrentKafkaListenerContainerFactory<String, String> listenerFactory =
                    (ConcurrentKafkaListenerContainerFactory<String, String>) factory;
            listenerFactory.setRecordInterceptor(new MqTraceRecordInterceptor());
        }
        return bean;
    }
}
