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

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk draft: the intermediate form during split and merge, before an id is assigned or embedding text is built
 * <p>
 * Merging must happen before assembly. Embedding text carries an outline-path prefix, so merging two finished
 * chunks would repeat that prefix. Indexes and chunk ids are assigned together at assembly
 *
 * @param content       display text, as written
 * @param embeddingBody retrieval body; assembly falls back to {@code content} when this is empty
 * @param piece         whether this is one piece of a split Block; see {@link #pieces}
 * @param heading       whether it contains a heading; see {@link #ofHeading}
 */
public record ChunkDraft(String content, String embeddingBody, ChunkMetadata metadata,
                         boolean piece, boolean heading) {

    public ChunkDraft {
        content = content == null ? "" : content;
        metadata = metadata == null ? ChunkMetadata.empty() : metadata;
    }

    public static ChunkDraft of(String content, ChunkMetadata metadata) {
        return new ChunkDraft(content, null, metadata, false, false);
    }

    public static ChunkDraft of(String content, String embeddingBody, ChunkMetadata metadata) {
        return new ChunkDraft(content, embeddingBody, metadata, false, false);
    }

    /**
     * Heading draft: a section marker for packing. One heading starts one section
     * <p>
     * Record presence, not level. Level is the parser's guess: MinerU infers it from font size, so a markdown
     * heading at level 3 can become level 2 in the PDF of the same document. Any rule that compares levels then
     * produces two different splits. Position is objective
     */
    public static ChunkDraft ofHeading(String content, String embeddingBody, ChunkMetadata metadata) {
        return new ChunkDraft(content, embeddingBody, metadata, false, true);
    }

    /**
     * Marks the pieces of one Block. A single piece means it was not split; return it unchanged
     * <p>
     * The chunker that cut a Block is the authority on how it splits, and merge must not undo that. Putting table
     * pieces cut at the row cap back together makes the cap meaningless. Paragraph pieces each carry overlap text,
     * and merging them copies that overlap into the same chunk
     */
    public static List<ChunkDraft> pieces(List<ChunkDraft> drafts) {
        if (drafts.size() <= 1) {
            return drafts;
        }
        List<ChunkDraft> marked = new ArrayList<>(drafts.size());
        for (ChunkDraft draft : drafts) {
            marked.add(new ChunkDraft(draft.content(), draft.embeddingBody(), draft.metadata(),
                    true, draft.heading()));
        }
        return marked;
    }

    /**
     * Retrieval body. Falls back to the display text when none was set explicitly
     */
    public String effectiveBody() {
        return StringUtils.hasText(embeddingBody) ? embeddingBody : content;
    }

    /**
     * Whether a retrieval body was set explicitly. Merge uses this to tell them apart; otherwise an image chunk's
     * URL-free retrieval body degrades into display text that still contains the URL
     */
    public boolean hasExplicitBody() {
        return StringUtils.hasText(embeddingBody);
    }
}
