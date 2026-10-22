package com.nageoffer.ai.ragent.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.AbstractException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Optional;

/**
 * Global exception handler.
 * Turns known exceptions into the uniform JSON envelope.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:100MB}")
    private String maxRequestSize;

    /**
     * Handles bean-validation failures on a request body.
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validExceptionHandler(HttpServletRequest request,
                                                             HttpServletResponse response,
                                                             MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.of(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return jsonError(response, Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr));
    }

    /**
     * Handles method-parameter validation failures.
     * Without this handler the failure falls through to the generic branch and the client only sees a system error.
     */
    @ExceptionHandler(value = HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> methodValidationExceptionHandler(HttpServletRequest request,
                                                                        HttpServletResponse response,
                                                                        HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse("Request parameters are invalid");
        log.warn("[{}] {} [ex] {}", request.getMethod(), getUrl(request), message);
        return jsonError(response, Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message));
    }

    /**
     * Handles exceptions thrown by application code.
     */
    @ExceptionHandler(value = {AbstractException.class})
    public ResponseEntity<Result<Void>> abstractException(HttpServletRequest request,
                                                         HttpServletResponse response,
                                                         AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex, ex.getCause());
            return jsonError(response, Results.failure(ex));
        }
        StringBuilder stackTraceBuilder = new StringBuilder();
        stackTraceBuilder.append(ex.getClass().getName()).append(": ").append(ex.getErrorMessage()).append("\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
        }
        log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), request.getRequestURL().toString(), ex, stackTraceBuilder);
        return jsonError(response, Results.failure(ex));
    }

    /**
     * Handles a missing login.
     */
    @ExceptionHandler(value = NotLoginException.class)
    public ResponseEntity<Result<Void>> notLoginException(HttpServletRequest request,
                                                         HttpServletResponse response,
                                                         NotLoginException ex) {
        log.warn("[{}] {} [auth] not-login: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return jsonError(response, Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "Not signed in or the session has expired"));
    }

    /**
     * Handles a missing role.
     */
    @ExceptionHandler(value = NotRoleException.class)
    public ResponseEntity<Result<Void>> notRoleException(HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        NotRoleException ex) {
        log.warn("[{}] {} [auth] no-role: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return jsonError(response, Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "Insufficient permissions"));
    }

    /**
     * Handles an upload that exceeds the size limit.
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> maxUploadSizeExceededException(HttpServletRequest request,
                                                                      HttpServletResponse response,
                                                                      MaxUploadSizeExceededException ex) {
        log.warn("[{}] {} [upload] uploaded file exceeds the size limit: {}", request.getMethod(), getUrl(request), ex.getMessage());
        String message;
        if (ex.getCause() instanceof IllegalStateException
                && ex.getCause().getCause() instanceof FileSizeLimitExceededException) {
            message = "Uploaded file exceeds the size limit. Maximum per file: " + maxFileSize;
        } else {
            message = "Upload request exceeds the size limit. Maximum per request: " + maxRequestSize;
        }
        return jsonError(response, Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message));
    }

    /**
     * Handles anything not matched above.
     */
    @ExceptionHandler(value = Throwable.class)
    public ResponseEntity<Result<Void>> defaultErrorHandler(HttpServletRequest request,
                                                           HttpServletResponse response,
                                                           Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return jsonError(response, Results.failure());
    }

    /**
     * Before the stream starts, return JSON so a strict SSE Accept header does not leave Result without a writable media type.
     * Keeps the existing HTTP 200 plus business error code. Once the stream is committed, the error is only logged.
     */
    private ResponseEntity<Result<Void>> jsonError(HttpServletResponse response, Result<Void> result) {
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    private String getUrl(HttpServletRequest request) {
        if (StrUtil.isBlank(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }
}
