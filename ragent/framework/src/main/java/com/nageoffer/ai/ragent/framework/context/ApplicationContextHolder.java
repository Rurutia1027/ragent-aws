package com.nageoffer.ai.ragent.framework.context;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Holder for the Spring application context.
 * Lets code outside the container look up beans.
 * ApplicationContext is injected at startup through ApplicationContextAware.
 * Static lookups for beans and for annotations declared on beans.
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext CONTEXT;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        ApplicationContextHolder.CONTEXT = applicationContext;
    }

    /**
     * Returns the bean of the given type.
     */
    public static <T> T getBean(Class<T> clazz) {
        return CONTEXT.getBean(clazz);
    }

    /**
     * Returns the bean with the given name.
     */
    public static Object getBean(String name) {
        return CONTEXT.getBean(name);
    }

    /**
     * Returns the bean with the given name and type.
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return CONTEXT.getBean(name, clazz);
    }

    /**
     * Returns every bean of the given type.
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return CONTEXT.getBeansOfType(clazz);
    }

    /**
     * Finds an annotation declared on a bean.
     */
    public static <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType) {
        return CONTEXT.findAnnotationOnBean(beanName, annotationType);
    }

    /**
     * Returns the application context.
     */
    public static ApplicationContext getInstance() {
        return CONTEXT;
    }
}
