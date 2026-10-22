package com.nageoffer.ai.ragent.framework.convention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Comparator;

/**
 * One retrieved RAG hit.
 * <p>
 * A single hit from vector search or another relevance search.
 * Holds the passage, its key, and the relevance score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RetrievedChunk {

    /**
     * Descending relevance. Missing and non-finite scores sort last.
     * The only ordering shared by every channel exit. Downstream truncation and RRF use this rank.
     * Cosine, BM25, and reciprocal rank are all finite. NaN and infinities only come from an upstream bug.
     * Float.compare treats NaN as the greatest value, so an unnormalized poison score would take the top rank.
     */
    public static final Comparator<RetrievedChunk> BY_SCORE_DESC = (a, b) -> Float.compare(sortScore(b), sortScore(a));

    private static float sortScore(RetrievedChunk chunk) {
        Float score = chunk.getScore();
        return score == null || !Float.isFinite(score) ? Float.NEGATIVE_INFINITY : score;
    }

    /**
     * Unique id of the hit.
     * For example the vector-store primary key or a document id.
     */
    private String id;

    /**
     * Hit text.
     * Usually a chunk or paragraph of the source document.
     */
    private String text;

    /**
     * Hit score.
     * Higher means more relevant to the query.
     */
    private Float score;

    /**
     * Rerank relevance, from 0 to 1.
     * Do not read {@link #score} for this. Cosine, BM25, reciprocal rank, and RRF all write that field, and a reader cannot tell which one.
     * When rerank is skipped or falls back to a no-op, score still holds whatever the previous writer stored.
     * Only a real rerank client writes this field. Null means rerank did not run.
     */
    private Float rerankScore;

    /**
     * Knowledge-base collection this hit came from.
     * Filled by each backend from its stored fields so intent can be tied to a collection. Null when there is no collection, such as web search.
     */
    private String collectionName;

    /**
     * Source document id.
     * Filled by metadata enrichment after retrieval. Null before that.
     */
    private String docId;

    /**
     * Zero-based index of this chunk inside its document.
     * Filled by metadata enrichment after retrieval. Null before that.
     */
    private Integer chunkIndex;

    /**
     * Source document name. Used as the internal title anchor when the context is assembled.
     * Filled by metadata enrichment after retrieval. Null before that.
     */
    private String docName;
}
