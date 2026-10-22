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

import cn.hutool.core.util.IdUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk assembler: the only path from drafts to finished chunks
 * <p>
 * Display text is stored as-is; outline context is added only to the embedding text. {@code content} is the
 * document as written, and headings already sit in the body where they appeared. A synthetic prefix would rewrite the source
 */
public final class ChunkAssembler {

    private static final String CONTEXT_SEPARATOR = "\n";

    private static final String OUTLINE_SEPARATOR = " / ";

    private ChunkAssembler() {
    }

    /**
     * Batch assembly: drafts must already be split and merged. Indexes are assigned from 0 in list order
     */
    public static List<Chunk> assembleAll(List<ChunkDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        List<Chunk> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            chunks.add(assemble(i, drafts.get(i)));
        }
        return chunks;
    }

    /**
     * Assembles one chunk and assigns a new chunk id
     */
    public static Chunk assemble(int index, ChunkDraft draft) {
        return assemble(nextChunkId(), index, draft);
    }

    /**
     * Assembles with an existing chunk id. Used when a manually edited chunk is stored again; a new id would
     * break the primary-key match between the relational store and the vector store
     */
    public static Chunk assemble(String chunkId, int index, ChunkDraft draft) {
        ChunkMetadata metadata = draft.metadata();
        return new Chunk(chunkId, index, draft.content(),
                composeEmbeddingText(metadata, draft.effectiveBody(), draft.content()), metadata);
    }

    /**
     * Restores a chunk already stored: the only correct entry for rebuilding vectors. Embedding text is taken
     * from the stored copy, not reassembled
     * <p>
     * Structure captured at ingest (outline path, a table's {@code column: value} rendering, an image description
     * with the URL removed) lives only in the {@code embedding_text} column. The relational row keeps display
     * text, so assembling again yields bare body text. When that column is empty, fall back to display text:
     * a manually created chunk's embedding text is the body
     */
    public static Chunk restore(String chunkId, int index, String content, String embeddingText) {
        return new Chunk(chunkId, index, content,
                StringUtils.hasText(embeddingText) ? embeddingText : content, ChunkMetadata.empty());
    }

    /**
     * Chunk id generation: the single point for the whole system
     */
    public static String nextChunkId() {
        return IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * Embedding text: the part of the outline path the body does not already cover, plus the body
     * <p>
     * When one section is split into several chunks, only the first body's text includes the headings. The path
     * prefix is the only outline wording later chunks have. Repeating those levels on the first chunk just writes
     * the section name twice and wastes space in the vector
     */
    private static String composeEmbeddingText(ChunkMetadata metadata, String body, String content) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, missingOutlinePrefix(metadata, content));
        appendIfPresent(sb, body);
        return sb.toString();
    }

    /**
     * The outline-path suffix the body does not already cover. The path runs from the root down, and a heading
     * the chunk already carries is necessarily a suffix, so cut at the first hit
     * <p>
     * A chunk that starts at {@code ### 1.3} carries the leaf heading but not the chapter. Dropping the whole
     * path would drop the chapter context too, so decide level by level rather than "skip the prefix if a heading is present"
     */
    private static String missingOutlinePrefix(ChunkMetadata metadata, String content) {
        List<String> path = metadata.outlinePath();
        int keep = 0;
        while (keep < path.size() && !content.contains(path.get(keep))) {
            keep++;
        }
        return keep == 0 ? null : String.join(OUTLINE_SEPARATOR, path.subList(0, keep));
    }

    private static void appendIfPresent(StringBuilder sb, String part) {
        if (!StringUtils.hasText(part)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(CONTEXT_SEPARATOR);
        }
        sb.append(part.strip());
    }
}
