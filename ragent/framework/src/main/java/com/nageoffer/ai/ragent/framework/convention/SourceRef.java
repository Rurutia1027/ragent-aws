package com.nageoffer.ai.ragent.framework.convention;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Document-level source citation.
 * <p>
 * Built by deduplicating retrieved passages per document and assigning numbers. Used for SSE, stored messages, and the source panel.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceRef {

    /**
     * Source number, starting at 1. The panel and inline citations share it.
     */
    private Integer index;

    /**
     * Document id, used to load the original for preview.
     */
    private String docId;

    /**
     * Document name, shown as the panel title.
     */
    private String docName;

    /**
     * Source type: file, url, or feishu.
     */
    private String sourceType;

    /**
     * File type such as md, xlsx, pdf, doc, or image. The UI picks an icon from it. Null for a web source.
     */
    private String fileType;

    /**
     * External URL for url and feishu sources. Null for a file, which is previewed through docId.
     */
    private String url;

    /**
     * Excerpt. A truncated slice of the document's most relevant passage.
     */
    private String excerpt;
}
