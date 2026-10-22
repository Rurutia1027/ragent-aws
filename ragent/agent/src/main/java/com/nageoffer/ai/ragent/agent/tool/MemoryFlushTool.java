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

package com.nageoffer.ai.ragent.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.agent.memory.AgentMemoryOutcome;
import com.nageoffer.ai.ragent.agent.memory.AgentMemoryOutcome.Status;
import com.nageoffer.ai.ragent.agent.memory.AgentMemoryPipeline;
import com.nageoffer.ai.ragent.agent.memory.AgentUserMemoryMiddleware;
import com.nageoffer.ai.ragent.agent.trace.AgentToolBodyTracer;
import com.nageoffer.ai.ragent.agent.trace.AgentTraceContextKeys;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;

/**
 * 记忆整理工具：模型只有触发权没有内容写入权，记什么忘什么由服务端仲裁
 * 身份取自 RuntimeContext 而非入参，模型改不了自己是谁
 */
@Slf4j
@RequiredArgsConstructor
public class MemoryFlushTool implements AgentTool {

    public static final String TOOL_NAME = "flush_memory";
    public static final String DISPLAY_NAME = "记忆整理";

    private final String description;
    private final AgentMemoryPipeline memoryPipeline;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 无参：给了参数就等于把内容写入权交出去，模型能凭空捏造要记的事实
     */
    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", false);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return AgentToolBodyTracer.trace(this, param, () -> Mono.fromCallable(() -> execute(param))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private ToolResultBlock execute(ToolCallParam param) {
        if (param == null) {
            return buildResult(null, "Tool call arguments cannot be empty", true);
        }
        String toolCallId = Optional.ofNullable(param.getToolUseBlock())
                .map(ToolUseBlock::getId)
                .orElse(null);
        RuntimeContext runtimeContext = param.getRuntimeContext();
        String userId = Optional.ofNullable(runtimeContext)
                .map(RuntimeContext::getUserId)
                .orElse(null);
        String conversationId = Optional.ofNullable(runtimeContext)
                .map(RuntimeContext::getSessionId)
                .orElse(null);
        // 本轮答复挂的那条用户消息就是本次请求；拿不到就判不了「处理到了没有」，不能硬跑后报成功
        String requestMessageId = runtimeContext == null
                ? null
                : runtimeContext.get(AgentTraceContextKeys.REPLY_TO_MESSAGE_ID) instanceof String id ? id : null;
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(conversationId) || StrUtil.isBlank(requestMessageId)) {
            log.warn("记忆整理工具拿不到会话身份或本次请求, 本次不整理");
            return buildResult(toolCallId, "This conversation cannot update memory, so nothing was saved", true);
        }
        try {
            AgentMemoryOutcome outcome = memoryPipeline.flush(userId, conversationId, requestMessageId);
            // 刷新认「记忆集变没变」不认「落了几条决策」：合并落库而决策全灭时 applied 为零、库已经变了
            if (outcome.mutated()) {
                refreshSnapshot(runtimeContext, userId);
            }
            log.info("记忆整理工具调用完成, userId: {}, conversationId: {}, 结局: {}, 落库: {}",
                    userId, conversationId, outcome.status(), outcome.applied());
            return render(toolCallId, outcome);
        } catch (Exception e) {
            log.error("记忆整理工具调用异常, userId: {}, conversationId: {}", userId, conversationId, e);
            return buildResult(toolCallId, "Memory update failed, so nothing was saved", true);
        }
    }

    /**
     * 落库已成事实，刷新失败只让本轮后续推理看到旧视图，不该把成功报成失败
     */
    private void refreshSnapshot(RuntimeContext runtimeContext, String userId) {
        try {
            runtimeContext.put(AgentUserMemoryMiddleware.SNAPSHOT_KEY, memoryPipeline.reloadSnapshot(userId));
        } catch (Exception e) {
            log.warn("长期记忆快照刷新失败, 本轮沿用旧视图, userId: {}", userId, e);
        }
    }

    /**
     * 没写进去的一律 ERROR，不留 default 确保新增枚举值编译期报错
     */
    private ToolResultBlock render(String toolCallId, AgentMemoryOutcome outcome) {
        if (outcome.cleared() && (outcome.status() == Status.WRITTEN || outcome.status() == Status.SETTLED_EMPTY)) {
            return buildResult(toolCallId, clearedText(outcome), false);
        }
        return switch (outcome.status()) {
            case WRITTEN -> buildResult(toolCallId, "Memory updated. Changes applied this time: " + outcome.applied() + " items", false);
            case SETTLED_EMPTY -> buildResult(toolCallId, outcome.mutated()
                    ? "Nothing new in this batch needed to be remembered. Existing memories were merged and shortened"
                    : "This batch is organized, and nothing in it needs to be remembered long term", false);
            case INCOMPLETE -> buildResult(toolCallId, "Only an earlier backlog was organized. This request was not reached, so nothing from it took effect", true);
            case NOTHING_PENDING -> buildResult(toolCallId, "Recent conversation is already organized. There is nothing new to process", false);
            // 门槛只挡后台批，flush 走到这里说明管道分叉错了
            case BELOW_THRESHOLD -> throw new IllegalStateException("Memory organization tool received a threshold outcome that belongs only to background extraction");
            case BUSY -> buildResult(toolCallId, "A memory update is already running. Nothing was processed. Please try again later", true);
            case CAPACITY_REJECTED -> buildResult(toolCallId, "Memory is at capacity, so nothing was saved", true);
            case DISABLED -> buildResult(toolCallId, "Memory is turned off, so this will not be remembered", true);
            // 快照过期是良性的：水位没推，同一区间下一轮 turn release 会重新抽一次
            case CONFLICT -> buildResult(toolCallId, "Memory was changed by another update, so nothing was saved. The next turn will try again", true);
            case FAILED -> buildResult(toolCallId, "Memory update failed, so nothing was saved", true);
        };
    }

    /**
     * 清空单列文案：普通的「生效 N 条」会被模型说成清空了，原本就空的也得如实交代
     * 清空之后只报生效变更数：跨批时后面几批还可能改写或撤回清空后记下的条目，说成「新记下」会多报
     */
    private String clearedText(AgentMemoryOutcome outcome) {
        if (outcome.applied() == 0) {
            return outcome.clearedItems() > 0
                    ? "Cleared all long-term memory. Removed " + outcome.clearedItems() + " items"
                    : "Long-term memory was already empty, and there is no long-term memory now";
        }
        String cleared = outcome.clearedItems() > 0
                ? "Cleared all previous long-term memory. Removed " + outcome.clearedItems() + " items"
                : "Long-term memory was already empty, so nothing needed to be removed";
        return cleared + "; after clearing, " + outcome.applied() + " memory changes took effect";
    }

    private ToolResultBlock buildResult(String toolCallId, String text, boolean isError) {
        return ToolResultBlock.builder()
                .id(toolCallId)
                .name(TOOL_NAME)
                .output(TextBlock.builder().text(text).build())
                .state(isError ? ToolResultState.ERROR : ToolResultState.SUCCESS)
                .build();
    }
}
