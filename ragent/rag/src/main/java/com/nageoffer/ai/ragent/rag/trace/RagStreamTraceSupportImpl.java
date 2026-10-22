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
import com.nageoffer.ai.ragent.framework.trace.RagStreamTraceSupport;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-thread stream trace: fixes @RagTraceNode AOP in the stream path
 * observing only the runAsync submission
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagStreamTraceSupportImpl implements RagStreamTraceSupport {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final RagTraceProperties traceProperties;
    private final RagTraceRecordService traceRecordService;

    @Override
    public StreamSpan beginStreamNode(String name, String type) {
        if (!traceProperties.isEnabled()) {
            return NOOP_SPAN;
        }
        String traceId = RagTraceContext.getTraceId();
        if (StrUtil.isBlank(traceId)) {
            return NOOP_SPAN;
        }

        String nodeId = IdUtil.getSnowflakeNextIdStr();
        String parentNodeId = RagTraceContext.currentNodeId();
        int depth = RagTraceContext.depth();
        long startMillis = System.currentTimeMillis();

        traceRecordService.startNode(RagTraceNodeDO.builder()
                .traceId(traceId)
                .nodeId(nodeId)
                .parentNodeId(parentNodeId)
                .depth(depth)
                .nodeType(StrUtil.blankToDefault(type, "STREAM"))
                .nodeName(name)
                .status(STATUS_RUNNING)
                .startTime(new Date())
                .build());

        // Push on the calling thread so later synchronous children (such as first-packet) can see the parent
        RagTraceContext.pushNode(nodeId);

        return new StreamSpanImpl(traceId, nodeId, startMillis);
    }

    private final class StreamSpanImpl implements StreamSpan {
        private final String traceId;
        private final String nodeId;
        private final long startMillis;
        private final AtomicBoolean detached = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);

        StreamSpanImpl(String traceId, String nodeId, long startMillis) {
            this.traceId = traceId;
            this.nodeId = nodeId;
            this.startMillis = startMillis;
        }

        @Override
        public void detach() {
            if (!detached.compareAndSet(false, true)) {
                return;
            }
            // Pop only when this node is on top of the stack, so concurrent nodes stay ordered
            if (nodeId.equals(RagTraceContext.currentNodeId())) {
                RagTraceContext.popNode();
            }
        }

        @Override
        public void finishSuccess() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                traceRecordService.finishNode(traceId, nodeId, STATUS_SUCCESS, null,
                        new Date(), System.currentTimeMillis() - startMillis);
            } catch (Exception e) {
                log.warn("stream trace finishSuccess 失败，traceId：{}，nodeId：{}", traceId, nodeId, e);
            }
        }

        @Override
        public void finishError(Throwable error) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                traceRecordService.finishNode(traceId, nodeId, STATUS_ERROR,
                        truncateError(error), new Date(), System.currentTimeMillis() - startMillis);
            } catch (Exception e) {
                log.warn("stream trace finishError 失败，traceId：{}，nodeId：{}", traceId, nodeId, e);
            }
        }

        @Override
        public void finishCancelledIfRunning() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                traceRecordService.finishNode(traceId, nodeId, STATUS_CANCELLED, null,
                        new Date(), System.currentTimeMillis() - startMillis);
            } catch (Exception e) {
                log.warn("stream trace finishCancelled 失败，traceId：{}，nodeId：{}", traceId, nodeId, e);
            }
        }
    }

    private String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName() + ": "
                + StrUtil.blankToDefault(throwable.getMessage(), "");
        int max = traceProperties.getMaxErrorLength();
        return message.length() <= max ? message : message.substring(0, max);
    }
}
