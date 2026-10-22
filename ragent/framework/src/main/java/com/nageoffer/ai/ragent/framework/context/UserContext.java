package com.nageoffer.ai.ragent.framework.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.nageoffer.ai.ragent.framework.exception.ClientException;

/**
 * Holder for the signed-in user. TTL copies it onto child threads.
 */
public final class UserContext {

    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    /**
     * Stores the user on the current thread.
     */
    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    /**
     * Returns the user on the current thread.
     */
    public static LoginUser get() {
        return CONTEXT.get();
    }

    /**
     * Returns the current user, or throws when nobody is signed in.
     */
    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new ClientException("Current signed-in user was not found");
        }
        return user;
    }

    /**
     * Current user id, or null when nobody is signed in.
     */
    public static String getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * Current username, or null when nobody is signed in.
     */
    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    /**
     * Current role, or null when nobody is signed in.
     */
    public static String getRole() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getRole();
    }

    /**
     * Current avatar, or null when nobody is signed in.
     */
    public static String getAvatar() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getAvatar();
    }

    /**
     * Clears the user from the current thread.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Whether a user is stored on the current thread.
     */
    public static boolean hasUser() {
        return CONTEXT.get() != null;
    }
}
