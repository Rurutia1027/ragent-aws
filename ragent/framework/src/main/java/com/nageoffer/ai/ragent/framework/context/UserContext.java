package com.nageoffer.ai.ragent.framework.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.nageoffer.ai.ragent.framework.exception.ClientException;

public final class UserContext {
    private static final TransmittableThreadLocal<LoginUser> CONTEXT =
            new TransmittableThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new ClientException("Current signed-in user was not found");
        }
        return user;
    }

    public static String getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    public static String getRole() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getRole();
    }

    public static String getAvatar() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getAvatar();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static boolean hasUser() {
        return CONTEXT.get() != null;
    }
}
