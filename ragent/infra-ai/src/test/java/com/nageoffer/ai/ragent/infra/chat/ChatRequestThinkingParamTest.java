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

package com.nageoffer.ai.ragent.infra.chat;

import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-format regression guard: enable_thinking is a DashScope-family private extension and may be sent only to providers that declare they understand it
 * <p>
 * One change sent the field unconditionally to every provider. OpenAI-family gateways rejected it as unknown_parameter with a 400,
 * and because that candidate often sits last in the tier it broke the whole fallback chain. This pins the request body per provider
 */
class ChatRequestThinkingParamTest {

    private static final String FIELD = "enable_thinking";
    private static final String THINKING_OBJECT = "thinking";

    @Test
    void bailianShouldCarryThinkingFlagWhenEnabled() {
        JsonObject body = buildBody(new BaiLianChatClient(), Boolean.TRUE);
        assertTrue(body.has(FIELD));
        assertTrue(body.get(FIELD).getAsBoolean());
    }

    /**
     * Qwen3 enables thinking by default unless it is explicitly turned off, so disabling it must send false rather than omit the field
     */
    @Test
    void bailianShouldExplicitlyDisableThinkingWhenClosed() {
        JsonObject body = buildBody(new BaiLianChatClient(), Boolean.FALSE);
        assertTrue(body.has(FIELD));
        assertFalse(body.get(FIELD).getAsBoolean());
    }

    @Test
    void bailianShouldTreatAbsentFlagAsDisabled() {
        JsonObject body = buildBody(new BaiLianChatClient(), null);
        assertTrue(body.has(FIELD));
        assertFalse(body.get(FIELD).getAsBoolean());
    }

    @Test
    void siliconFlowShouldCarryThinkingFlag() {
        assertTrue(buildBody(new SiliconFlowChatClient(), Boolean.TRUE).has(FIELD));
        assertTrue(buildBody(new SiliconFlowChatClient(), Boolean.FALSE).has(FIELD));
    }

    /**
     * DeepSeek's dialect is a thinking object. Sending enable_thinking is rejected as an unknown parameter
     */
    @Test
    void deepSeekShouldNeverCarryThinkingFlag() {
        assertFalse(buildBody(new DeepSeekChatClient(), Boolean.TRUE).has(FIELD));
        assertFalse(buildBody(new DeepSeekChatClient(), Boolean.FALSE).has(FIELD));
        assertFalse(buildBody(new DeepSeekChatClient(), null).has(FIELD));
    }

    /**
     * V4 enables thinking by default. Disabling it must send disabled rather than omit the whole object
     */
    @Test
    void deepSeekShouldCarryThinkingObject() {
        assertEquals("enabled", thinkingType(buildBody(new DeepSeekChatClient(), Boolean.TRUE)));
        assertEquals("disabled", thinkingType(buildBody(new DeepSeekChatClient(), Boolean.FALSE)));
        assertEquals("disabled", thinkingType(buildBody(new DeepSeekChatClient(), null)));
    }

    /**
     * The aggregate gateway speaks native OpenAI. Sending this field is rejected as an unknown parameter
     */
    @Test
    void aiHubMixShouldNeverCarryThinkingFlag() {
        assertFalse(buildBody(new AIHubMixChatClient(), Boolean.TRUE).has(FIELD));
        assertFalse(buildBody(new AIHubMixChatClient(), Boolean.FALSE).has(FIELD));
        assertFalse(buildBody(new AIHubMixChatClient(), null).has(FIELD));
    }

    @Test
    void ollamaShouldNeverCarryThinkingFlag() {
        assertFalse(buildBody(new OllamaChatClient(), Boolean.TRUE).has(FIELD));
        assertFalse(buildBody(new OllamaChatClient(), Boolean.FALSE).has(FIELD));
    }

    /**
     * The thinking object is a private extension too, and may be sent only to DeepSeek
     */
    @Test
    void otherProvidersShouldNeverCarryThinkingObject() {
        assertFalse(buildBody(new BaiLianChatClient(), Boolean.TRUE).has(THINKING_OBJECT));
        assertFalse(buildBody(new AIHubMixChatClient(), Boolean.TRUE).has(THINKING_OBJECT));
        assertFalse(buildBody(new OllamaChatClient(), Boolean.TRUE).has(THINKING_OBJECT));
    }

    /**
     * A new provider does not know this field by default, so it is not sent unconditionally again
     */
    @Test
    void unknownProviderShouldOptOutByDefault() {
        AbstractOpenAIStyleChatClient client = new AbstractOpenAIStyleChatClient() {
            @Override
            public String provider() {
                return "brand-new-gateway";
            }

            @Override
            public String chat(ChatRequest request, ModelTarget target) {
                return doChat(request, target);
            }

            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
                return doStreamChat(request, callback, target);
            }
        };
        assertFalse(buildBody(client, Boolean.TRUE).has(FIELD));
    }

    /**
     * Streaming and sync share request-body construction. The thinking field must not change with the stream flag
     */
    @Test
    void streamBodyShouldFollowSameThinkingRule() {
        JsonObject bailian = new BaiLianChatClient().buildRequestBody(request(Boolean.TRUE), target(), true);
        assertTrue(bailian.has(FIELD));
        assertTrue(bailian.get("stream").getAsBoolean());
        assertFalse(new AIHubMixChatClient().buildRequestBody(request(Boolean.TRUE), target(), true).has(FIELD));
    }

    /**
     * Sampling parameters are outside the thinking gate, so changing the gate must not damage shared fields
     */
    @Test
    void commonSamplingFieldsShouldStayIntact() {
        JsonObject body = buildBody(new AIHubMixChatClient(), Boolean.FALSE);
        assertEquals("test-model", body.get("model").getAsString());
        assertEquals(0D, body.get("temperature").getAsDouble());
        assertEquals(1, body.getAsJsonArray("messages").size());
    }

    private JsonObject buildBody(AbstractOpenAIStyleChatClient client, Boolean thinking) {
        return client.buildRequestBody(request(thinking), target(), false);
    }

    private String thinkingType(JsonObject body) {
        assertTrue(body.has(THINKING_OBJECT), "请求体缺少 thinking 对象");
        return body.getAsJsonObject(THINKING_OBJECT).get("type").getAsString();
    }

    private ChatRequest request(Boolean thinking) {
        return ChatRequest.builder()
                .messages(List.of(ChatMessage.user("你好")))
                .temperature(0D)
                .thinking(thinking)
                .build();
    }

    private ModelTarget target() {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setModel("test-model");
        return new ModelTarget("test-id", candidate, new AIModelProperties.ProviderConfig(), null);
    }
}
