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

package com.nageoffer.ai.ragent.rag.trace;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.infra.chat.ForwardingStreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Consumer;

/**
 * Streaming chat trace wrapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamChatTraceRunner {

    private static final String ENTRY_METHOD = "RAGChatService#streamChat";
    private static final String TRACE_NAME = "rag-stream-chat";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";
    private static final String USER_TTFT_NODE_NAME = "user-first-packet";
    private static final String USER_TTFT_NODE_TYPE = "USER_TTFT";

    private final RagTraceProperties traceProperties;
    private final RagTraceRecordService traceRecordService;

    /**
     * @param businessLogic receives the trace-enhanced callback: onComplete / onError trigger finishRun.
     *                      The implementor injects that callback into the pipeline context and runs the pipeline
     */
    public void run(String question,
                    String conversationId,
                    String taskId,
                    StreamCallback callback,
                    Consumer<StreamCallback> businessLogic) {
        if (!traceProperties.isEnabled()) {
            runWithoutTrace(conversationId, taskId, callback, businessLogic);
            return;
        }

        String traceId = IdUtil.getSnowflakeNextIdStr();
        long startMillis = System.currentTimeMillis();
        traceRecordService.startRun(RagTraceRunDO.builder()
                .traceId(traceId)
                .traceName(TRACE_NAME)
                .entryMethod(ENTRY_METHOD)
                .conversationId(conversationId)
                .taskId(taskId)
                .userId(UserContext.getUserId())
                .status(STATUS_RUNNING)
                .startTime(new Date())
                .extraData(JSONUtil.createObj()
                        .set("questionLength", StrUtil.length(question))
                        .set("question", question)
                        .toString())
                .build());

        Date runStartTime = new Date(startMillis);
        StreamCallback traceAwareCallback = new ForwardingStreamCallback(callback) {
            @Override
            protected void onFirstContent() {
                recordUserTtft(traceId, runStartTime, startMillis);
            }

            @Override
            protected void onFinish(boolean success, Throwable error) {
                finishRun(traceId, success, error, startMillis);
            }
        };

        RagTraceContext.setTraceId(traceId);
        RagTraceContext.setTaskId(taskId);
        try {
            businessLogic.accept(traceAwareCallback);
        } catch (Throwable ex) {
            log.warn("执行流式对话失败（同步阶段），会话ID：{}，任务ID：{}", conversationId, taskId, ex);
            // Route through traceAwareCallback.onError to reuse its CAS and avoid a second finish after the pipeline already reached a terminal state
            try {
                traceAwareCallback.onError(ex);
            } catch (Throwable ignored) {
                // Finish the trace even if callback.onError fails
            }
        } finally {
            // Clear ThreadLocal as soon as the synchronous phase ends, so pooled threads are not polluted;
            // Async threads already hold a traceId snapshot via TTL and do not depend on this thread's ThreadLocal
            RagTraceContext.clear();
        }
    }

    /**
     * Record user-perceived first-token TTFT: from run start (pipeline entry) to the first character pushed to the frontend.
     * Reflects full-path prefix cost (routing / rewrite / intent / retrieval / LLM first token, and so on)
     */
    private void recordUserTtft(String traceId, Date runStartTime, long startMillis) {
        long now = System.currentTimeMillis();
        long durationMs = Math.max(0, now - startMillis);
        String nodeId = IdUtil.getSnowflakeNextIdStr();
        try {
            traceRecordService.startNode(RagTraceNodeDO.builder()
                    .traceId(traceId)
                    .nodeId(nodeId)
                    .depth(0)
                    .nodeType(USER_TTFT_NODE_TYPE)
                    .nodeName(USER_TTFT_NODE_NAME)
                    .status(STATUS_RUNNING)
                    .startTime(runStartTime)
                    .build());
            traceRecordService.finishNode(traceId, nodeId, STATUS_SUCCESS, null, new Date(now), durationMs);
        } catch (Exception e) {
            log.warn("写入 user-first-packet 节点失败，traceId：{}", traceId, e);
        }
    }

    private void finishRun(String traceId, boolean success, Throwable error, long startMillis) {
        try {
            traceRecordService.finishRun(
                    traceId,
                    success ? STATUS_SUCCESS : STATUS_ERROR,
                    success ? null : truncateError(error),
                    new Date(),
                    System.currentTimeMillis() - startMillis
            );
        } catch (Exception e) {
            log.warn("finishRun 失败，traceId：{}", traceId, e);
        }
    }

    private void runWithoutTrace(String conversationId,
                                 String taskId,
                                 StreamCallback callback,
                                 Consumer<StreamCallback> businessLogic) {
        try {
            businessLogic.accept(callback);
        } catch (Throwable ex) {
            log.warn("执行流式对话失败，会话ID：{}，任务ID：{}", conversationId, taskId, ex);
            callback.onError(ex);
        }
    }

    private String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName() + ": " + StrUtil.blankToDefault(throwable.getMessage(), "");
        int max = traceProperties.getMaxErrorLength();
        return message.length() <= max ? message : message.substring(0, max);
    }
}
