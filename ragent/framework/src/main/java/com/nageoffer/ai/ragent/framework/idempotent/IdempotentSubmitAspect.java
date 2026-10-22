package com.nageoffer.ai.ragent.framework.idempotent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Aspect that stops a user from submitting the same form twice.
 */
@Aspect
@Component
@RequiredArgsConstructor
public final class IdempotentSubmitAspect {

    private final RedissonClient redissonClient;
    private final Gson gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(
                    MultipartFile.class,
                    (JsonSerializer<MultipartFile>) (file, type, context) -> {
                        JsonObject json = new JsonObject();
                        json.addProperty("name", file.getName());
                        json.addProperty("originalFilename", file.getOriginalFilename());
                        json.addProperty("contentType", file.getContentType());
                        json.addProperty("size", file.getSize());
                        return json;
                    })
            .create();

    @Value("${ragent.eval.enabled:false}")
    private boolean evalEnabled;

    /**
     * Advice for methods annotated with {@link IdempotentSubmit}.
     */
    @Around("@annotation(com.nageoffer.ai.ragent.framework.idempotent.IdempotentSubmit)")
    public Object idempotentSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        if (evalEnabled) {
            return joinPoint.proceed();
        }
        IdempotentSubmit idempotentSubmit = getIdempotentSubmitAnnotation(joinPoint);
        // Build the distributed lock key.
        String lockKey = buildLockKey(joinPoint, idempotentSubmit);
        RLock lock = redissonClient.getLock(lockKey);
        // If the lock cannot be acquired, this is a duplicate submit and the call fails.
        if (!lock.tryLock()) {
            throw new ClientException(idempotentSubmit.message());
        }
        Object result;
        try {
            // Run the annotated method.
            result = joinPoint.proceed();
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * @return the {@link IdempotentSubmit} annotation
     */
    public static IdempotentSubmit getIdempotentSubmitAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return targetMethod.getAnnotation(IdempotentSubmit.class);
    }

    /**
     * @return the servlet path of the current request
     */
    private String getServletPath() {
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.requireNonNull(sra).getRequest().getServletPath();
    }

    /**
     * @return the current user id
     */
    private String getCurrentUserId() {
        return UserContext.getUserId();
    }

    /**
     * @return joinPoint md5
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(gson.toJson(joinPoint.getArgs()).getBytes(StandardCharsets.UTF_8));
    }

    private String buildLockKey(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) {
        if (StrUtil.isNotBlank(idempotentSubmit.key())) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object keyValue = SpELUtil.parseKey(idempotentSubmit.key(), signature.getMethod(), joinPoint.getArgs());
            return String.format("idempotent-submit:key:%s", keyValue);
        }
        return String.format(
                "idempotent-submit:path:%s:currentUserId:%s:md5:%s",
                getServletPath(),
                getCurrentUserId(),
                calcArgsMD5(joinPoint)
        );
    }
}
