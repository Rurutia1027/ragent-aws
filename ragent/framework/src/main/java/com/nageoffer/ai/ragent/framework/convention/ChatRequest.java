package com.nageoffer.ai.ragent.framework.convention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral chat request.
 *
 * <p>
 * Holds the context and sampling controls for one chat call. Adapters turn this
 * single input into each provider's protocol, including Ollama, Bailian, and OpenAI.
 * The adapter layer is the only place that knows a provider's wire format.
 * </p>
 *
 * <p>Typical use:</p>
 * <pre>
 * ChatRequest req = ChatRequest.builder()
 *     .temperature(0.3)
 *     .maxTokens(512)
 *     .build();
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

    /**
     * Full message list.
     * <p>
     * Pass a system/user/assistant sequence directly.
     * When messages is not empty, the adapter builds the request from this field.
     * A prompt is appended as an extra user message.
     * </p>
     */
    @Default
    private List<ChatMessage> messages = new ArrayList<>();

    // ================== sampling controls ==================

    /**
     * Sampling temperature, usually between 0 and 2.
     * <p>
     * Lower values are more stable. Higher values are more varied.
     * Question answering often uses 0.1 to 0.5. Open-ended writing often uses 0.7 or higher.
     * </p>
     */
    private Double temperature;

    /**
     * Nucleus sampling (top-p).
     * <p>
     * Samples from the smallest token set whose cumulative probability reaches P. Often combined with {@link #temperature}.
     * Usually 0.8 to 0.95. Lower is more conservative.
     * Null keeps the model default.
     * </p>
     */
    private Double topP;

    /**
     * Top-k sampling.
     * <p>
     * Each step samples only from the K most probable tokens. Often combined with {@link #temperature}
     * or {@link #topP}. Smaller K is more conservative.
     * Null keeps the model default.
     * </p>
     */
    private Integer topK;

    /**
     * Maximum number of tokens the model may generate for this reply.
     * <p>
     * Limits length and cost. Null keeps the model or server default.
     * </p>
     */
    private Integer maxTokens;

    /**
     * Optional switch for reasoning mode.
     * <p>
     * Reserved for models that can return a reasoning trace.
     * What it means depends on the provider, for example whether intermediate reasoning is returned.
     * Providers that do not support it should ignore this field.
     * </p>
     */
    private Boolean thinking;

    /**
     * Optional switch for tool calling.
     * <p>
     * Reserved so a later request can allow tool calls:
     * <ul>
     *   <li>{@code false}: text only</li>
     *   <li>{@code true}: the model may call the declared tools</li>
     * </ul>
     * The tool list and the handling of tool results belong to the caller or the provider adapter.
     * </p>
     */
    private Boolean enableTools;
}
