package com.nageoffer.ai.ragent.framework.convention;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One chat message.
 *
 * <p>
 * A vendor-neutral chat message, with a role and content:
 * <ul>
 *   <li>{@link Role#SYSTEM}: system prompt that sets behavior and rules</li>
 *   <li>{@link Role#USER}: the user's input</li>
 *   <li>{@link Role#ASSISTANT}: the model reply</li>
 * </ul>
 * The same shape is used across model providers.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * Message role.
     */
    public enum Role {
        /**
         * System role. Sets rules, identity, and style.
         */
        SYSTEM,

        /**
         * User role. The person's question or input.
         */
        USER,

        /**
         * Assistant role. The model reply.
         */
        ASSISTANT;

        /**
         * Matches a role from its string name.
         *
         * @param value role name, compared case-insensitively
         * @return the matching {@link Role}
         * @throws IllegalArgumentException when the string matches no role
         */
        public static Role fromString(String value) {
            for (Role role : Role.values()) {
                if (role.name().equalsIgnoreCase(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Invalid role type: " + value);
        }
    }

    /**
     * How the message ended.
     */
    public enum MessageStatus {
        NORMAL,
        INTERRUPTED,
        REJECTED
    }

    /**
     * Role of this message: system, user, or assistant.
     */
    private Role role;

    /**
     * Text content.
     */
    private String content;

    /**
     * Reasoning text. Only an assistant message may carry it.
     */
    private String thinkingContent;

    /**
     * Reasoning duration in seconds. Only an assistant message may carry it.
     */
    private Integer thinkingDuration;

    /**
     * Document-level sources. Only an assistant message may carry them.
     */
    private List<SourceRef> sources;

    /**
     * Grounding passages for suggested follow-up questions. Stored with an assistant message and kept out of the model context.
     */
    private List<GroundingChunk> retrievedChunks;

    /**
     * Id of the user message this assistant message answers.
     */
    private String replyToMessageId;

    /**
     * How the message ended.
     */
    private MessageStatus messageStatus = MessageStatus.NORMAL;

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public ChatMessage(Role role, String content, MessageStatus messageStatus) {
        this.role = role;
        this.content = content;
        this.messageStatus = messageStatus;
    }

    /**
     * Creates a system message.
     *
     * @param content system prompt
     * @return a {@link ChatMessage} with role {@link Role#SYSTEM}
     */
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    /**
     * Creates a user message.
     *
     * @param content user input
     * @return a {@link ChatMessage} with role {@link Role#USER}
     */
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    /**
     * Creates an assistant message.
     *
     * @param content assistant reply
     * @return a {@link ChatMessage} with role {@link Role#ASSISTANT}
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    /**
     * Creates an assistant message that includes reasoning text.
     *
     * @param content         assistant reply
     * @param thinkingContent reasoning text
     * @return a {@link ChatMessage} with role {@link Role#ASSISTANT}
     */
    public static ChatMessage assistant(String content, String thinkingContent) {
        return assistant(content, thinkingContent, null);
    }

    /**
     * Creates an assistant message that includes reasoning text and its duration.
     *
     * @param content          assistant reply
     * @param thinkingContent  reasoning text
     * @param thinkingDuration reasoning duration in seconds
     * @return a {@link ChatMessage} with role {@link Role#ASSISTANT}
     */
    public static ChatMessage assistant(String content, String thinkingContent, Integer thinkingDuration) {
        ChatMessage message = new ChatMessage(Role.ASSISTANT, content);
        message.setThinkingContent(thinkingContent);
        message.setThinkingDuration(thinkingDuration);
        return message;
    }
}
