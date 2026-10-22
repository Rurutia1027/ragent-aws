package com.nageoffer.ai.ragent.framework.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of the signed-in user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginUser {

    /**
     * User id.
     */
    private String userId;

    /**
     * Username.
     */
    private String username;

    /**
     * Role, such as admin or user.
     */
    private String role;

    /**
     * Avatar.
     */
    private String avatar;
}
