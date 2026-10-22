/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.user.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * User-context interceptor.
 *
 * <p>Loads the logged-in user from Sa-Token before the request is handled and stores it in
 * UserContext for later business logic. Clears UserContext after the request to avoid leaks.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read the logged-in user id from Sa-Token in preHandle</li>
 *   <li>Load the full user record from the database</li>
 *   <li>Wrap the user as a LoginUser and store it on the UserContext thread</li>
 *   <li>Clear UserContext after the request so a reused thread does not keep stale data</li>
 *   <li>Skip async dispatches (such as an SSE completion callback) where the Sa-Token context is already gone</li>
 * </ul>
 *
 * @author nageoffer
 * @see UserContext
 * @see LoginUser
 */
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/583231?v=4";

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        // Skip async dispatches (an SSE completion callback triggers asyncDispatch after the Sa-Token context is gone)
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        // Allow preflight requests through so CORS is not blocked
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String loginId = StpUtil.getLoginIdAsString();
        UserDO user = userMapper.selectById(loginId);

        UserContext.set(
                LoginUser.builder()
                        .userId(user.getId().toString())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .avatar(StrUtil.isBlank(user.getAvatar()) ? DEFAULT_AVATAR_URL : user.getAvatar())
                        .build()
        );
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        UserContext.clear();
    }
}
