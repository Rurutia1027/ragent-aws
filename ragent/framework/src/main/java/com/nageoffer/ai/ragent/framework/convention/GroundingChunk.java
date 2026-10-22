package com.nageoffer.ai.ragent.framework.convention;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Grounding passage for a suggested follow-up question.
 * <p>
 * The highest-scoring passage per document, truncated and stored with the assistant message.
 * Used so a follow-up stays inside evidence the system can answer, and does not repeat the answer already given.
 * <p>
 * Separate from {@link SourceRef}, which feeds the source panel and preview with a short excerpt.
 * This type feeds follow-up generation and keeps a longer passage. Neither is added to the model context.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroundingChunk {

    /**
     * Document name, so a follow-up can tell which document the evidence came from.
     */
    private String docName;

    /**
     * Passage text used as evidence when a follow-up is generated.
     */
    private String text;
}
