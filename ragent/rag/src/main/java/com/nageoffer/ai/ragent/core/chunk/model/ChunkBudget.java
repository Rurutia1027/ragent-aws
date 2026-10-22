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

package com.nageoffer.ai.ragent.core.chunk.model;

/**
 * Chunk budget: the only chunking freedom the user can configure
 * <p>
 * How to cut is decided solely by document structure. The user only controls size and redundancy. Fine-grained
 * per-Block parameters (list-item caps and the like) are not exposed; they stay internal constants of each chunker
 *
 * @param overlapChars    overlap in characters between adjacent chunks; must be less than maxChars
 * @param rowsPerChunk    max data rows per chunk for tables
 * @param toleranceFactor factor by which a chunk may exceed maxChars to stay intact; see {@link #toleranceChars()}
 */
public record ChunkBudget(int maxChars, int overlapChars, int rowsPerChunk, int toleranceFactor) {

    /**
     * Sentinel for a single whole-document chunk. Built by {@link #wholeDocument()} and recognized by
     * {@link #isWholeDocument()}. Callers do not need to know the value itself
     */
    private static final int WHOLE_DOCUMENT = Integer.MAX_VALUE;

    private static final int DEFAULT_TOLERANCE_FACTOR = 3;

    private static final int DEFAULT_MAX_CHARS = 1024;

    /**
     * Default overlap is this fraction of the chunk size
     */
    public static final int OVERLAP_DIVISOR = 8;

    /** Upper bound on the tolerance factor. The product is still capped by {@link #MAX_CHARS_LIMIT}; this only rejects an obviously wrong magnitude */
    public static final int TOLERANCE_FACTOR_LIMIT = 8;

    /**
     * Upper bound on chunk size
     * <p>
     * The real hard limit is the embedding model's input length. This is a conservative value every model in use
     * can accept, and it is enforced at construction. Otherwise an oversized chunk fails only at embed time
     */
    public static final int MAX_CHARS_LIMIT = 8192;

    /**
     * Upper bound on rows per table chunk, also checked at construction
     */
    public static final int ROWS_PER_CHUNK_LIMIT = 1000;

    public ChunkBudget {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be > 0, actual " + maxChars);
        }
        // Whole-document mode is "no budget" by definition; neither cap applies to it
        if (maxChars != WHOLE_DOCUMENT) {
            if (maxChars > MAX_CHARS_LIMIT) {
                throw new IllegalArgumentException("maxChars must not exceed " + MAX_CHARS_LIMIT + ", actual " + maxChars);
            }
            if (rowsPerChunk > ROWS_PER_CHUNK_LIMIT) {
                throw new IllegalArgumentException("rowsPerChunk must not exceed " + ROWS_PER_CHUNK_LIMIT + ", actual " + rowsPerChunk);
            }
        }
        if (overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("overlapChars must be in [0, maxChars), actual " + overlapChars);
        }
        if (rowsPerChunk <= 0) {
            throw new IllegalArgumentException("rowsPerChunk must be > 0, actual " + rowsPerChunk);
        }
        if (toleranceFactor < 1 || toleranceFactor > TOLERANCE_FACTOR_LIMIT) {
            throw new IllegalArgumentException("toleranceFactor must be in [1, " + TOLERANCE_FACTOR_LIMIT
                    + "], actual " + toleranceFactor);
        }
    }

    /** Three-argument constructor: tolerance factor takes the default, for callers that do not expose that setting yet */
    public ChunkBudget(int maxChars, int overlapChars, int rowsPerChunk) {
        this(maxChars, overlapChars, rowsPerChunk, DEFAULT_TOLERANCE_FACTOR);
    }

    /**
     * Default overlap for a given chunk size
     * <p>
     * Overlap is not only redundancy. It is also the maximum distance {@link com.nageoffer.ai.ragent.core.chunk.text.TextSplitter}
     * walks back to find sentence-ending punctuation. Too small, and the cut lands mid-sentence: 1024 with 64 often
     * fails to reach a full stop in a long CJK sentence, so overlap scales with chunk size
     */
    public static int defaultOverlapFor(int maxChars) {
        return Math.max(0, Math.min(maxChars - 1, maxChars / OVERLAP_DIVISOR));
    }

    /**
     * System default budget: the one default for the whole system
     * <p>
     * {@code maxChars} is 1024 rather than the common 512. Semantic dilution is prevented by {@code ChunkPacker}'s
     * section boundaries, not by shrinking chunks. {@code RetrieveRequest.topK} defaults to 5, and chunks that are
     * too small let fragments of one section fill the slots. The only thing it actually cuts is an overlong prose paragraph
     */
    public static ChunkBudget defaults() {
        return new ChunkBudget(DEFAULT_MAX_CHARS, defaultOverlapFor(DEFAULT_MAX_CHARS), 50);
    }

    /**
     * Do not split the document: the full text is one chunk
     */
    public static ChunkBudget wholeDocument() {
        return new ChunkBudget(WHOLE_DOCUMENT, 0, WHOLE_DOCUMENT);
    }

    /**
     * Character count a chunk may grow to in order to stay intact. {@code maxChars} is a target, not a hard cap;
     * cutting a semantic unit costs more than exceeding the target
     * <p>
     * Two callers, at stages that do not conflict: each chunker uses it to decide whether to split a Block (table,
     * code block), and {@code ChunkPacker} uses it to decide whether a whole section can stay one chunk. The product
     * is still capped by {@link #MAX_CHARS_LIMIT}
     */
    public int toleranceChars() {
        return isWholeDocument() ? WHOLE_DOCUMENT : Math.min(maxChars * toleranceFactor, MAX_CHARS_LIMIT);
    }

    public boolean isWholeDocument() {
        return maxChars == WHOLE_DOCUMENT;
    }
}
